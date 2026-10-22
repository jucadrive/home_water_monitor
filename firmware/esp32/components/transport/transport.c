#include "transport.h"
#include "frame.h"
#include "mqtt_client.h"
#include "mqtt5_client.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_wifi.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

typedef struct { bool receiver; uint32_t generation; } callback_context;
static hwm_transport_config cfg;
static QueueHandle_t queue;
static atomic_bool overflow;
static esp_mqtt_client_handle_t rx, tx;
static callback_context rx_context = {.receiver = true}, tx_context;
static hwm_frame frame;
static char preservation_topic[112], events_topic[112], rx_id[96], tx_id[96];
static bool wifi_up, wifi_pending, receiver_ready;
static uint64_t wifi_deadline, wifi_next, rx_deadline, rx_next;
static unsigned wifi_backoff = 1000, rx_backoff = 1000;
static int subscription_id;
static unsigned wifi_reason;

static void post(const hwm_net_event *event)
{
    if (xQueueSend(queue, event, 0) != pdTRUE) atomic_store(&overflow, true);
}
static void wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg; (void)data;
    hwm_net_event e = {0};
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) e.kind = HWM_NET_WIFI_UP;
    else if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        e.kind = HWM_NET_WIFI_DOWN;
        e.msg_id = ((wifi_event_sta_disconnected_t *)data)->reason;
    }
    else return;
    post(&e);
}
static void mqtt_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)base;
    callback_context *context = arg;
    esp_mqtt_event_handle_t m = data;
    hwm_net_event e = {.generation = context->generation, .msg_id = m->msg_id, .success = m->protocol_ver == MQTT_PROTOCOL_V_5};
    if (id == MQTT_EVENT_CONNECTED) e.kind = context->receiver ? HWM_NET_RX_CONNECTED : HWM_NET_TX_CONNECTED;
    else if (id == MQTT_EVENT_DISCONNECTED || id == MQTT_EVENT_ERROR) {
        if (id == MQTT_EVENT_ERROR && m->error_handle) {
            printf("HWM_TRANSPORT_ERROR receiver=%d type=%d esp=%d socket=%d connect=%d\n", context->receiver,
                m->error_handle->error_type, m->error_handle->esp_tls_last_esp_err,
                m->error_handle->esp_transport_sock_errno, m->error_handle->connect_return_code);
        }
        e.kind = context->receiver ? HWM_NET_RX_DOWN : HWM_NET_TX_DOWN;
        if (context->receiver) frame.active = false;
    } else if (id == MQTT_EVENT_SUBSCRIBED && context->receiver) {
        e.kind = HWM_NET_RX_READY;
        e.success = e.success && m->data_len == 1 && m->data && (unsigned char)m->data[0] == 1;
    } else if (id == MQTT_EVENT_PUBLISHED && !context->receiver) {
        e.kind = HWM_NET_PUBACK;
        /* Pinned SDK exposes the optional MQTT 5 reason byte in event.data. Omitted means success. */
        e.success = e.success && (m->data_len == 0 || (m->data_len == 1 && m->data && (unsigned char)m->data[0] < 0x80));
    } else if (id == MQTT_EVENT_DATA && context->receiver) {
        bool metadata = e.success && m->qos == 1 && !m->retain && m->topic
            && m->topic_len == (int)strlen(preservation_topic) && !memcmp(m->topic, preservation_topic, m->topic_len)
            && m->property && m->property->payload_format_indicator && m->property->content_type
            && m->property->content_type_len == 16 && !memcmp(m->property->content_type, "application/json", 16);
        bool was_active = frame.active;
        int result = hwm_frame_feed(&frame, m->total_data_len, m->current_data_offset, m->data, m->data_len, metadata);
        if (result == 0) return;
        /* One invalid frame produces one notification; discard its remaining fragments without flooding the queue. */
        if (result < 0 && m->current_data_offset != 0 && !was_active) return;
        e.kind = result == 1 && hwm_parse_confirmation(frame.body, frame.used, &e.confirmation)
            ? HWM_NET_CONFIRMATION : HWM_NET_INVALID_CONFIRMATION;
        /* Only bounded value data crosses tasks; SDK strings/properties expire after this callback. */
    } else return;
    post(&e);
}

static bool destroy(esp_mqtt_client_handle_t *client)
{
    if (!*client) return true;
    /* stop waits for SDK task exit, which deletes all outbox items; destroy releases its storage. */
    esp_err_t stopped = esp_mqtt_client_stop(*client);
    if (stopped != ESP_OK) return false;
    if (esp_mqtt_client_destroy(*client) != ESP_OK) return false;
    *client = NULL;
    return true;
}
static esp_mqtt_client_handle_t create_client(bool receiver, callback_context *context)
{
    esp_mqtt_client_config_t options = {
        .broker.address.uri = cfg.broker_uri,
        .credentials.client_id = receiver ? rx_id : tx_id,
        .credentials.username = cfg.username,
        .credentials.authentication.password = cfg.password,
        .session.protocol_ver = MQTT_PROTOCOL_V_5,
        .session.keepalive = 30,
        .session.disable_clean_session = false,
        .session.message_retransmit_timeout = 60000,
        .network.timeout_ms = 10000,
        .network.disable_auto_reconnect = true,
        .task.stack_size = 6144,
        .buffer.size = 512,
        .buffer.out_size = receiver ? 1024 : 17408,
        .outbox.limit = 18432,
    };
    esp_mqtt_client_handle_t client = esp_mqtt_client_init(&options);
    if (!client) return NULL;
    esp_mqtt5_connection_property_config_t properties = {
        .session_expiry_interval = 0, .maximum_packet_size = 17408, .receive_maximum = 1
    };
    if (esp_mqtt5_client_set_connect_property(client, &properties) != ESP_OK
        || esp_mqtt_client_register_event(client, ESP_EVENT_ANY_ID, mqtt_event, context) != ESP_OK
        || esp_mqtt_client_start(client) != ESP_OK) {
        esp_mqtt_client_destroy(client);
        return NULL;
    }
    return client;
}
static void reset_rx(uint64_t now)
{
    receiver_ready = false;
    if (!destroy(&rx)) {
        printf("HWM_FAULT category=rx_stop\n");
        return;
    }
    frame.active = false;
    rx_next = now + rx_backoff;
    if (rx_backoff < 16000) rx_backoff *= 2;
}

bool hwm_transport_init(const hwm_transport_config *config)
{
    cfg = *config;
    queue = xQueueCreate(24, sizeof(hwm_net_event));
    if (!queue || nvs_flash_init() != ESP_OK || esp_netif_init() != ESP_OK || esp_event_loop_create_default() != ESP_OK) return false;
    if (!esp_netif_create_default_wifi_sta()) return false;
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    if (esp_wifi_init(&init) != ESP_OK || esp_wifi_set_storage(WIFI_STORAGE_RAM) != ESP_OK) return false;
    if (esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event, NULL) != ESP_OK
        || esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event, NULL) != ESP_OK) return false;
    wifi_config_t wifi = {0};
    size_t ssid_len = strlen(cfg.ssid), password_len = strlen(cfg.wifi_password);
    if (ssid_len == 0 || ssid_len > sizeof(wifi.sta.ssid) || password_len > sizeof(wifi.sta.password)) return false;
    if (cfg.open_wifi ? password_len != 0 : (password_len < 8 || password_len > 63)) return false;
    memcpy(wifi.sta.ssid, cfg.ssid, ssid_len);
    memcpy(wifi.sta.password, cfg.wifi_password, password_len);
    /* Empty passwords never implicitly downgrade a protected configuration. */
    wifi.sta.threshold.authmode = cfg.open_wifi ? WIFI_AUTH_OPEN : WIFI_AUTH_WPA2_PSK;
    if (esp_wifi_set_mode(WIFI_MODE_STA) != ESP_OK || esp_wifi_set_config(WIFI_IF_STA, &wifi) != ESP_OK || esp_wifi_start() != ESP_OK) return false;
    snprintf(preservation_topic, sizeof(preservation_topic), "home/water/v1/%s/preservation", cfg.device_id);
    snprintf(events_topic, sizeof(events_topic), "home/water/v1/%s/events", cfg.device_id);
    snprintf(rx_id, sizeof(rx_id), "hwm-%s-rx", cfg.device_id);
    snprintf(tx_id, sizeof(tx_id), "hwm-%s-tx", cfg.device_id);
    return true;
}

void hwm_transport_poll(uint64_t now)
{
    if (!wifi_up) {
        if (rx) reset_rx(now);
        if (wifi_pending && now >= wifi_deadline) {
            esp_wifi_disconnect(); wifi_pending = false; wifi_next = now + wifi_backoff;
            if (wifi_backoff < 16000) wifi_backoff *= 2;
        }
        if (!wifi_pending && now >= wifi_next) {
            wifi_pending = esp_wifi_connect() == ESP_OK;
            wifi_deadline = now + 10000;
            if (!wifi_pending) wifi_next = now + wifi_backoff;
        }
        return;
    }
    if (rx && !receiver_ready && now >= rx_deadline) reset_rx(now);
    if (!rx && now >= rx_next) {
        rx_context.generation++;
        rx = create_client(true, &rx_context);
        rx_deadline = now + 10000;
        if (!rx) reset_rx(now);
    }
}

bool hwm_transport_receive(hwm_net_event *event, uint64_t now)
{
    if (atomic_exchange(&overflow, false)) {
        reset_rx(now); esp_wifi_disconnect(); wifi_up = wifi_pending = false;
        *event = (hwm_net_event){.kind = HWM_NET_OVERFLOW};
        return true;
    }
    if (xQueueReceive(queue, event, 0) != pdTRUE) return false;
    if (event->kind == HWM_NET_WIFI_UP) {
        wifi_up = true; wifi_pending = false; wifi_backoff = 1000; wifi_reason = 0;
    } else if (event->kind == HWM_NET_WIFI_DOWN) {
        wifi_up = wifi_pending = false;
        wifi_reason = (unsigned)event->msg_id;
        receiver_ready = false;
        wifi_next = now + wifi_backoff;
        if (wifi_backoff < 16000) wifi_backoff *= 2;
    } else if (event->kind == HWM_NET_RX_CONNECTED || event->kind == HWM_NET_RX_READY || event->kind == HWM_NET_RX_DOWN
               || event->kind == HWM_NET_CONFIRMATION || event->kind == HWM_NET_INVALID_CONFIRMATION) {
        if (!rx || event->generation != rx_context.generation) { event->kind = HWM_NET_NONE; return true; }
        if (event->kind == HWM_NET_RX_CONNECTED) {
            esp_mqtt5_subscribe_property_config_t properties = {.retain_as_published_flag = true, .retain_handle = 2};
            if (!event->success || esp_mqtt5_client_set_subscribe_property(rx, &properties) != ESP_OK
                || (subscription_id = esp_mqtt_client_subscribe(rx, preservation_topic, 1)) < 0) reset_rx(now);
        } else if (event->kind == HWM_NET_RX_READY) {
            receiver_ready = event->success && event->msg_id == subscription_id;
            if (receiver_ready) { rx_backoff = 1000; printf("HWM_RX_READY mqtt=5\n"); }
            else reset_rx(now);
        } else if (event->kind == HWM_NET_RX_DOWN) reset_rx(now);
    }
    return true;
}

bool hwm_transport_rx_ready(void) { return wifi_up && receiver_ready; }
bool hwm_transport_wifi_up(void) { return wifi_up; }
unsigned hwm_transport_wifi_reason(void) { return wifi_reason; }
bool hwm_transport_tx_active(void) { return tx != NULL; }
bool hwm_transport_start_tx(uint32_t generation)
{
    if (tx || !hwm_transport_rx_ready()) return false;
    tx_context.generation = generation;
    tx = create_client(false, &tx_context);
    return tx != NULL;
}
int hwm_transport_publish(const char *payload, size_t length)
{
    if (!tx || !payload || !length || length > HWM_MAX_PAYLOAD) return -1;
    esp_mqtt5_publish_property_config_t properties = {.payload_format_indicator = true, .content_type = "application/json"};
    if (esp_mqtt5_client_set_publish_property(tx, &properties) != ESP_OK) return -1;
    return esp_mqtt_client_enqueue(tx, events_topic, payload, length, 1, 0, true);
}
bool hwm_transport_stop_tx(void) { return destroy(&tx); }
unsigned hwm_random_jitter(unsigned cap)
{
    if (!cap) return 0;
    unsigned range = cap + 1;
    uint32_t limit = UINT32_MAX - UINT32_MAX % range;
    uint32_t random;
    do { random = esp_random(); } while (random >= limit);
    return random % range;
}
