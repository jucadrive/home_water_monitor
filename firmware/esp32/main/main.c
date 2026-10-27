#include <stdio.h>
#include <string.h>
#include "sdkconfig.h"
#if defined(HWM_PERSONAL_CONFIG) && !defined(CONFIG_MBEDTLS_HAVE_TIME_DATE)
#error "Personal TLS requires certificate validity time verification"
#endif
#include "session.h"
#include "control.h"
#include "command_control.h"
#include "transport.h"
#include "ads1115.h"
#include "tds_sensor.h"
#include "worker.h"
#if defined(HWM_PERSONAL_CONFIG) && !defined(HWM_CREDENTIAL_FREE)
#include "local_personal_config.h"
#elif !defined(HWM_CREDENTIAL_FREE) && __has_include("local_config.h")
#include "local_config.h"
#else
#include "local_config.example.h"
#endif
#ifndef HWM_MQTT_CA_PEM
#define HWM_MQTT_CA_PEM ""
#endif
#include "driver/uart.h"
#include "esp_log.h"
#include "esp_system.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static hwm_session session;
static bool fatal;
static hwm_command_control control;
static char status_payload[HWM_CONTROL_MAX + 1];
static bool reported;
static bool sensor_failed;
static hwm_sensor_worker sensor_worker;
static uint32_t sensor_generation;
#ifndef HWM_REAL_SENSOR
#define HWM_REAL_SENSOR 0
#endif
#ifndef HWM_CONTROL_MQTT
#define HWM_CONTROL_MQTT 1
#endif
static uint64_t now_ms(void) { return esp_timer_get_time() / 1000; }
static void uuid(char out[37])
{
    unsigned char bytes[16];
    esp_fill_random(bytes, sizeof(bytes));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    snprintf(out, 37, "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
}
static void report(const char *probe)
{
    control.ready = hwm_transport_rx_ready();
    /* A sensor fault must not disable RETRY of an already frozen result. */
    control.fault = control.fault || fatal || (sensor_failed && session.state == HWM_IDLE);
    size_t length = hwm_status_json(&control, &session, now_ms(), probe, status_payload, sizeof(status_payload));
    if (length) hwm_transport_status(status_payload, length);
    reported = true;
}
static void sensor_failure(const char *reason, uint64_t measured, unsigned window_count)
{
    sensor_failed = true;
    hwm_worker_cancel(&sensor_worker, sensor_generation);
    printf("HWM_SENSOR_FAULT category=acquisition valid_samples=%u buffer_retained=1 reason=%s elapsed_ms=%llu due_ms=%llu window_count=%u\n",
        session.count, reason, (unsigned long long)(measured - session.started_ms),
        (unsigned long long)(session.deadline_ms - session.started_ms), window_count);
    if (!hwm_finish_incomplete(&session, now_ms())) {
        fatal = true;
        printf("HWM_FAULT category=sensor_finalize buffer_retained=1\n");
    }
}
static void start_sensor(void)
{
    if (!HWM_REAL_SENSOR || session.state != HWM_MEASURING) return;
    if (++sensor_generation == 0) sensor_generation = 1;
    if (!hwm_worker_start(&sensor_worker, sensor_generation, session.target, session.started_ms))
        sensor_failure("worker_start", now_ms(), 0);
}
static void sample(void)
{
    if (!HWM_REAL_SENSOR) {
        if (hwm_sample_simulated(&session, now_ms()) && session.count % 10 == 0)
            printf("HWM_SAMPLE_COUNT samples=%u\n", session.count);
        return;
    }
    hwm_worker_result result;
    for (unsigned i = 0; i < HWM_WORKER_CAPACITY && hwm_worker_receive(&sensor_worker, &result); i++) {
        if (result.generation != sensor_generation) {
            printf("HWM_SENSOR_DISCARDED reason=generation\n"); continue;
        }
        if (session.state != HWM_MEASURING) {
            /* A consumer fault already froze its valid prefix; queued later readings cannot change it. */
            printf("HWM_SENSOR_DISCARDED reason=finalized\n"); continue;
        }
        if (result.error != HWM_WORKER_OK) {
            sensor_failure(hwm_worker_error_name(result.error), result.at_ms, result.window_count);
        } else if (result.seq != session.count + 1 || !hwm_sample_append_observed(&session,
                       result.at_ms, now_ms(), result.value.tds_hundredths, result.value.voltage_hundredths)) {
            sensor_failure("session_append", result.at_ms, 0);
        } else {
            printf("HWM_SENSOR_SAMPLE seq=%u tds_hundredths=%ld voltage_hundredths=%ld\n",
                session.count, (long)result.value.tds_hundredths, (long)result.value.voltage_hundredths);
        }
    }
    uint32_t failed_generation = hwm_worker_take_failure(&sensor_worker);
    if (failed_generation == sensor_generation && failed_generation && session.state == HWM_MEASURING)
        sensor_failure("queue", now_ms(), 0);
}
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
    if (HWM_CONTROL_MQTT) { printf("HWM_COMMAND_REJECTED reason=control_mode\n"); return; }
    bool accepted = !fatal && !control.fault && (cmd.type == HWM_COMMAND_START
        ? hwm_start(&session, cmd.session_id, HWM_SAMPLE_TARGET, now_ms()) : hwm_retry(&session, cmd.session_id, now_ms()));
    if (accepted && cmd.type == HWM_COMMAND_START) start_sensor();
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
            hwm_control_released(&control, &event->confirmation);
            report(NULL);
            printf("HWM_BUFFER_RELEASED session=%s samples=0\n", session.last_session_id); status();
        } else printf("HWM_CONFIRM_REJECTED reason=correlation\n");
    } else if (event->kind == HWM_NET_INVALID_CONFIRMATION) printf("HWM_CONFIRM_REJECTED reason=contract\n");
    else if (event->kind == HWM_NET_COMMAND) {
        control.ready = hwm_transport_rx_ready();
        control.fault = control.fault || fatal;
        if (event->command.probe) {
            char window[37]; uuid(window);
            hwm_control_probe(&control, &session, window, now);
            report(event->command.probe_id);
        } else {
            hwm_state before = session.state;
            hwm_control_execute(&control, &session, &event->command, now);
            if (before == HWM_IDLE && session.state == HWM_MEASURING) start_sensor();
            report(NULL);
        }
    } else if (event->kind == HWM_NET_INVALID_COMMAND) printf("HWM_CONTROL_REJECTED reason=contract\n");
}

void app_main(void)
{
    esp_log_level_set("*", ESP_LOG_NONE);
    if (!strlen(HWM_WIFI_SSID) || (!HWM_WIFI_OPEN && !strlen(HWM_WIFI_PASSWORD)) || !strlen(HWM_MQTT_PASSWORD)) {
        printf("HWM_FAULT category=configuration\n"); return;
    }
    hwm_transport_config config = {.ssid = HWM_WIFI_SSID, .wifi_password = HWM_WIFI_PASSWORD,
        .broker_uri = HWM_BROKER_URI, .device_id = HWM_DEVICE_ID, .username = HWM_MQTT_USERNAME,
        .password = HWM_MQTT_PASSWORD, .open_wifi = HWM_WIFI_OPEN, .ca_pem = HWM_MQTT_CA_PEM};
    uart_config_t uart = {.baud_rate = 115200, .data_bits = UART_DATA_8_BITS, .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1, .flow_ctrl = UART_HW_FLOWCTRL_DISABLE, .source_clk = UART_SCLK_DEFAULT};
    if (!hwm_init(&session, HWM_DEVICE_ID) || uart_param_config(UART_NUM_0, &uart) != ESP_OK
        || uart_driver_install(UART_NUM_0, 512, 0, 0, NULL, 0) != ESP_OK || !hwm_transport_init(&config)) {
        printf("HWM_FAULT category=initialization\n"); return;
    }
    char boot[37]; uuid(boot);
    if (!hwm_control_init(&control, HWM_DEVICE_ID, boot, HWM_CONTROL_MQTT)) {
        printf("HWM_FAULT category=control_initialization\n"); return;
    }
    hwm_set_real_sensor(&session, HWM_REAL_SENSOR);
    if (HWM_REAL_SENSOR) {
        sensor_failed = !hwm_ads1115_init()
            || !hwm_worker_init(&sensor_worker, hwm_ads1115_read, NULL, HWM_WORKER_CAPACITY);
        control.fault = sensor_failed;
        printf("HWM_SENSOR_INIT ready=%d address=0x48 calibration=%s\n", !sensor_failed, HWM_SENSOR_CALIBRATION);
    }
    printf("HWM_BOOT mqtt=5 simulated=%d target=%d mode=%s\n", !HWM_REAL_SENSOR, HWM_SAMPLE_TARGET, HWM_CONTROL_MQTT ? "MQTT" : "UART");
    hwm_state last_state = HWM_IDLE;
    unsigned last_attempt = 0;
    bool last_ready = false;
    uint64_t heartbeat = 0;
    while (1) {
        reported = false;
        serial_poll();
        if (!fatal) {
            hwm_net_event event;
            /* A finite queue bounds callback pressure; timers still run after at most 24 events. */
            for (unsigned i = 0; i < 24 && hwm_transport_receive(&event, now_ms()); i++) network_event(&event);
            hwm_transport_poll(now_ms());
            sample();
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
            if (!reported) report(NULL);
        }
        bool ready = hwm_transport_rx_ready();
        if (!reported && (ready != last_ready || now_ms() >= heartbeat)) report(NULL);
        if (reported) heartbeat = now_ms() + 15000;
        last_ready = ready;
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
