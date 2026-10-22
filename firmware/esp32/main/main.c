#include <stdio.h>
#include <string.h>
#include "session.h"
#include "control.h"
#include "transport.h"
#if !defined(HWM_CREDENTIAL_FREE) && __has_include("local_config.h")
#include "local_config.h"
#else
#include "local_config.example.h"
#endif
#include "driver/uart.h"
#include "esp_log.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static hwm_session session;
static bool fatal;
static uint64_t now_ms(void) { return esp_timer_get_time() / 1000; }
static void status(void)
{
    printf("HWM_STATUS state=%s session=%s samples=%u finalized=%d attempt=%u round=%u wifi_up=%d wifi_reason=%u rx_ready=%d tx_active=%d heap=%lu min_heap=%lu\n",
        hwm_state_name(session.state), session.session_id[0] ? session.session_id : "none", session.count,
        session.finalized, session.attempt, session.round, hwm_transport_wifi_up(), hwm_transport_wifi_reason(), hwm_transport_rx_ready(), hwm_transport_tx_active(),
        (unsigned long)esp_get_free_heap_size(), (unsigned long)esp_get_minimum_free_heap_size());
}
static bool stop_tx(void)
{
    if (hwm_transport_stop_tx()) return true;
    fatal = true;
    printf("HWM_FAULT category=tx_stop buffer_retained=1\n");
    return false;
}
static void command(const char *line)
{
    hwm_command cmd;
    if (!hwm_parse_command(line, &cmd)) { printf("HWM_COMMAND_REJECTED reason=syntax\n"); return; }
    if (cmd.type == HWM_COMMAND_STATUS) { status(); return; }
    bool accepted = !fatal && (cmd.type == HWM_COMMAND_START
        ? hwm_start(&session, cmd.session_id, HWM_SAMPLE_TARGET, now_ms()) : hwm_retry(&session, cmd.session_id, now_ms()));
    printf("HWM_COMMAND_%s type=%s\n", accepted ? "ACCEPTED" : "REJECTED", cmd.type == HWM_COMMAND_START ? "START" : "RETRY");
    status();
}
static void serial_poll(void)
{
    static char line[80];
    static size_t used;
    static bool discard;
    unsigned char bytes[64];
    int count = uart_read_bytes(UART_NUM_0, bytes, sizeof(bytes), 0);
    for (int i = 0; i < count; i++) {
        unsigned char c = bytes[i];
        if (c == '\n') {
            if (used && line[used - 1] == '\r') used--;
            line[used] = 0;
            if (discard) printf("HWM_COMMAND_REJECTED reason=line\n");
            else command(line);
            used = 0; discard = false;
        } else if (!discard) {
            if (!c || c > 126 || (c < 32 && c != '\r') || used >= sizeof(line) - 1) discard = true;
            else line[used++] = (char)c;
        }
    }
}
static void network_event(const hwm_net_event *event)
{
    uint64_t now = now_ms();
    unsigned jitter = hwm_random_jitter(hwm_retry_cap_ms(&session));
    if (event->kind == HWM_NET_TX_CONNECTED && event->generation == session.generation && session.state == HWM_PREPARING) {
        if (!event->success || now >= session.deadline_ms || !hwm_transport_rx_ready()) { hwm_fail(&session, now, jitter); stop_tx(); return; }
        int id = hwm_transport_publish(session.payload, session.payload_len);
        if (!hwm_submitted(&session, id, now_ms())) { hwm_fail(&session, now_ms(), jitter); stop_tx(); }
        else printf("HWM_TX_ENQUEUED attempt=%u generation=%lu\n", session.attempt, (unsigned long)session.generation);
    } else if (event->kind == HWM_NET_PUBACK) {
        if (hwm_puback(&session, event->generation, event->msg_id, event->success, now, jitter)) {
            printf("HWM_PUBACK success=%d\n", event->success); stop_tx();
        }
    } else if ((event->kind == HWM_NET_TX_DOWN && event->generation == session.generation)
               || event->kind == HWM_NET_OVERFLOW || event->kind == HWM_NET_WIFI_DOWN) {
        /* A lost RX link after PUBACK does not shorten the independent 30-second confirmation wait. */
        if (session.state == HWM_PREPARING || session.state == HWM_WAIT_PUBACK) hwm_fail(&session, now, jitter);
        stop_tx();
    } else if (event->kind == HWM_NET_CONFIRMATION) {
        if (hwm_confirmation_matches(&session, &event->confirmation) && stop_tx() && hwm_release(&session, &event->confirmation)) {
            printf("HWM_BUFFER_RELEASED session=%s samples=0\n", session.last_session_id); status();
        } else printf("HWM_CONFIRM_REJECTED reason=correlation\n");
    } else if (event->kind == HWM_NET_INVALID_CONFIRMATION) printf("HWM_CONFIRM_REJECTED reason=contract\n");
}

void app_main(void)
{
    esp_log_level_set("*", ESP_LOG_NONE);
    if (!strlen(HWM_WIFI_SSID) || (!HWM_WIFI_OPEN && !strlen(HWM_WIFI_PASSWORD)) || !strlen(HWM_MQTT_PASSWORD)) {
        printf("HWM_FAULT category=configuration\n"); return;
    }
    hwm_transport_config config = {.ssid = HWM_WIFI_SSID, .wifi_password = HWM_WIFI_PASSWORD,
        .broker_uri = HWM_BROKER_URI, .device_id = HWM_DEVICE_ID, .username = HWM_MQTT_USERNAME,
        .password = HWM_MQTT_PASSWORD, .open_wifi = HWM_WIFI_OPEN};
    uart_config_t uart = {.baud_rate = 115200, .data_bits = UART_DATA_8_BITS, .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1, .flow_ctrl = UART_HW_FLOWCTRL_DISABLE, .source_clk = UART_SCLK_DEFAULT};
    if (!hwm_init(&session, HWM_DEVICE_ID) || uart_param_config(UART_NUM_0, &uart) != ESP_OK
        || uart_driver_install(UART_NUM_0, 512, 0, 0, NULL, 0) != ESP_OK || !hwm_transport_init(&config)) {
        printf("HWM_FAULT category=initialization\n"); return;
    }
    printf("HWM_BOOT mqtt=5 simulated=1 target=%d\n", HWM_SAMPLE_TARGET);
    hwm_state last_state = HWM_IDLE;
    unsigned last_attempt = 0;
    while (1) {
        serial_poll();
        if (!fatal) {
            hwm_net_event event;
            /* A finite queue bounds callback pressure; timers still run after at most 24 events. */
            for (unsigned i = 0; i < 24 && hwm_transport_receive(&event, now_ms()); i++) network_event(&event);
            hwm_transport_poll(now_ms());
            if (session.state == HWM_MEASURING && hwm_sample_simulated(&session, now_ms()) && session.count % 10 == 0)
                printf("HWM_SAMPLE_COUNT samples=%u\n", session.count);
            hwm_state before = session.state;
            hwm_tick(&session, now_ms(), hwm_random_jitter(hwm_retry_cap_ms(&session)));
            if (before != session.state && (session.state == HWM_BACKOFF || session.state == HWM_MANUAL_WAIT)) stop_tx();
            if (!fatal && session.state == HWM_PREPARING && !hwm_transport_tx_active() && hwm_transport_rx_ready()) {
                if (!hwm_transport_start_tx(session.generation)) hwm_fail(&session, now_ms(), hwm_random_jitter(hwm_retry_cap_ms(&session)));
            }
        }
        if (last_state != session.state || last_attempt != session.attempt) {
            printf("HWM_STATE state=%s attempt=%u round=%u samples=%u payload_bytes=%u\n", hwm_state_name(session.state),
                session.attempt, session.round, session.count, (unsigned)session.payload_len);
            last_state = session.state; last_attempt = session.attempt;
        }
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
