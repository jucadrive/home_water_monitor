#include "command_control.h"
#include "unity.h"
#include <string.h>

static hwm_session s;
static hwm_command_control c;
static hwm_control_message m;
static const char *boot = "11111111-1111-4111-8111-111111111111";
static const char *window = "22222222-2222-4222-8222-222222222222";
static const char *other = "33333333-3333-4333-8333-333333333333";
static const char *start = "{\"schemaVersion\":1,\"eventType\":\"START\",\"deviceId\":\"esp32-bench\","
    "\"commandId\":\"44444444-4444-4444-8444-444444444444\",\"sessionId\":\"55555555-5555-4555-8555-555555555555\","
    "\"targetBootId\":\"11111111-1111-4111-8111-111111111111\",\"windowId\":\"22222222-2222-4222-8222-222222222222\","
    "\"expectedSamples\":60,\"samplingIntervalMs\":1000}";
static char json[HWM_CONTROL_MAX + 2];
static void init(void)
{
    TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_control_init(&c, "esp32-bench", boot, true));
    TEST_ASSERT_TRUE(hwm_parse_control(start, strlen(start), &m));
    c.ready = true;
    hwm_control_probe(&c, &s, window, 100);
}
static void start_and_duplicate(void)
{
    init(); TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 101));
    TEST_ASSERT_EQUAL(HWM_MEASURING, s.state); TEST_ASSERT_FALSE(c.window_live);
    TEST_ASSERT_TRUE(hwm_sample_simulated(&s, 1101));
    TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 20000));
    TEST_ASSERT_EQUAL(1, s.count); TEST_ASSERT_EQUAL(101, s.started_ms);
}
static void changed_command_conflicts(void)
{
    init(); TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 101));
    strcpy(m.session_id, other);
    TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 102));
    TEST_ASSERT_EQUAL_STRING("COMMAND_CONFLICT", c.reason);
    TEST_ASSERT_NOT_EQUAL(0, strcmp(s.session_id, other));
}
static void expired_boundary(void)
{
    init(); TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 10100));
    TEST_ASSERT_EQUAL_STRING("WINDOW_EXPIRED", c.reason); TEST_ASSERT_EQUAL(HWM_IDLE, s.state);
    init(); TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 10099));
}
static void probe_replaces_window(void)
{
    init(); hwm_control_probe(&c, &s, other, 200);
    TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 201));
    TEST_ASSERT_EQUAL_STRING("WINDOW_INVALID", c.reason);
    strcpy(m.window_id, other); TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 201));
}
static void boot_guard(void)
{
    init(); strcpy(m.boot_id, other); TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 101));
    TEST_ASSERT_EQUAL_STRING("BOOT_MISMATCH", c.reason); TEST_ASSERT_TRUE(c.window_live);
}
static void modes_and_fault(void)
{
    init(); c.mqtt_mode = false; TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 101));
    TEST_ASSERT_EQUAL_STRING("NOT_READY", c.reason);
    hwm_control_probe(&c, &s, window, 200); TEST_ASSERT_FALSE(c.window_live);
    init(); c.fault = true; TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 101));
    TEST_ASSERT_EQUAL_STRING("DEVICE_FAULT", c.reason);
}
static void retry_once(void)
{
    init(); TEST_ASSERT_TRUE(hwm_start(&s, m.session_id, 0, 0)); s.state = HWM_MANUAL_WAIT;
    m.retry = true; hwm_control_probe(&c, &s, window, 100);
    TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 101)); TEST_ASSERT_EQUAL(2, s.round);
    TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 102)); TEST_ASSERT_EQUAL(2, s.round);
    TEST_ASSERT_EQUAL(0, s.count); TEST_ASSERT_TRUE(s.finalized);
}
static void retry_session_guard(void)
{
    init(); m.retry = true; TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 101));
    TEST_ASSERT_EQUAL_STRING("NO_PENDING_BUFFER", c.reason);
    TEST_ASSERT_TRUE(hwm_start(&s, other, 0, 0)); s.state = HWM_MANUAL_WAIT;
    TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 102));
    TEST_ASSERT_EQUAL_STRING("SESSION_MISMATCH", c.reason);
}
static void strict_command_json(void)
{
    init(); TEST_ASSERT_FALSE(hwm_parse_control(start, strlen(start) - 1, &m));
    strcpy(json, start); json[strlen(json) - 1] = ',';
    strcat(json, "\"schemaVersion\":1}"); TEST_ASSERT_FALSE(hwm_parse_control(json, strlen(json), &m));
    strcpy(json, start); json[strlen(json) - 1] = ',';
    strcat(json, "\"extra\":1}"); TEST_ASSERT_FALSE(hwm_parse_control(json, strlen(json), &m));
    strcpy(json, start); char *p = strstr(json, ":60"); p[1] = '5';
    TEST_ASSERT_FALSE(hwm_parse_control(json, strlen(json), &m));
    TEST_ASSERT_FALSE(hwm_uuid4("11111111-1111-1111-8111-111111111111"));
}
static void command_size_and_probe(void)
{
    size_t n = strlen(start); memcpy(json, start, n); memset(json + n, ' ', sizeof(json) - n);
    TEST_ASSERT_TRUE(hwm_parse_control(json, HWM_CONTROL_MAX, &m));
    TEST_ASSERT_FALSE(hwm_parse_control(json, HWM_CONTROL_MAX + 1, &m));
    const char *probe = "{\"schemaVersion\":1,\"eventType\":\"STATE_REQUEST\",\"deviceId\":\"esp32-bench\","
        "\"probeId\":\"33333333-3333-4333-8333-333333333333\"}";
    TEST_ASSERT_TRUE(hwm_parse_control(probe, strlen(probe), &m)); TEST_ASSERT_TRUE(m.probe);
}
static void status_and_release_history(void)
{
    init(); size_t n = hwm_status_json(&c, &s, 101, other, json, sizeof(json));
    TEST_ASSERT_GREATER_THAN(0, n); TEST_ASSERT_NOT_NULL(strstr(json, "\"controlWindow\":{\"windowId\":"));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"controlReady\":true"));
    hwm_confirmation release = {.normal = false, .count = 0}; strcpy(release.session_id, m.session_id);
    hwm_control_released(&c, &release);
    TEST_ASSERT_TRUE(hwm_control_execute(&c, &s, &m, 102));
    TEST_ASSERT_GREATER_THAN(0, hwm_status_json(&c, &s, 103, NULL, json, sizeof(json)));
    TEST_ASSERT_NOT_NULL(strstr(json, "PRESERVATION_CONFIRMED"));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"lastExecution\":{\"commandId\":"));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"controlWindow\":null"));
}
static void status_bounds_and_reboot(void)
{
    init(); TEST_ASSERT_EQUAL(0, hwm_status_json(&c, &s, 101, NULL, json, 10));
    c.status_seq = 2147483647U;
    TEST_ASSERT_EQUAL(0, hwm_status_json(&c, &s, 102, NULL, json, sizeof(json))); TEST_ASSERT_TRUE(c.fault);
    TEST_ASSERT_TRUE(hwm_control_init(&c, "esp32-bench", other, true));
    TEST_ASSERT_FALSE(c.has_execution); TEST_ASSERT_FALSE(c.has_release); TEST_ASSERT_EQUAL(0, c.status_seq);
}
static void round_limit_retains_buffer(void)
{
    init(); TEST_ASSERT_TRUE(hwm_start(&s, m.session_id, 0, 0));
    s.state = HWM_MANUAL_WAIT; s.round = 2147483647U;
    TEST_ASSERT_FALSE(hwm_retry(&s, m.session_id, 101));
    m.retry = true; TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 102));
    TEST_ASSERT_EQUAL_STRING("DEVICE_FAULT", c.reason); TEST_ASSERT_TRUE(c.fault);
    TEST_ASSERT_EQUAL(2147483647U, s.round); TEST_ASSERT_EQUAL(HWM_MANUAL_WAIT, s.state);
    TEST_ASSERT_TRUE(s.finalized); TEST_ASSERT_GREATER_THAN(0, s.payload_len);
    TEST_ASSERT_GREATER_THAN(0, hwm_status_json(&c, &s, 103, other, json, sizeof(json)));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"round\":2147483647"));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"controlWindow\":null"));
}
static void final_status_disables_new_execution(void)
{
    init(); c.status_seq = 2147483646U;
    TEST_ASSERT_GREATER_THAN(0, hwm_status_json(&c, &s, 101, other, json, sizeof(json)));
    TEST_ASSERT_EQUAL(2147483647U, c.status_seq); TEST_ASSERT_TRUE(c.fault);
    TEST_ASSERT_NOT_NULL(strstr(json, "\"controlReady\":false"));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"fault\":\"CONTROL_UNAVAILABLE\""));
    TEST_ASSERT_NOT_NULL(strstr(json, "\"controlWindow\":null"));
    TEST_ASSERT_FALSE(hwm_control_execute(&c, &s, &m, 102)); TEST_ASSERT_EQUAL(HWM_IDLE, s.state);
    TEST_ASSERT_EQUAL(0, hwm_status_json(&c, &s, 103, NULL, json, sizeof(json)));
    TEST_ASSERT_EQUAL(2147483647U, c.status_seq);
}
void hwm_run_command_tests(void)
{
    RUN_TEST(start_and_duplicate); RUN_TEST(changed_command_conflicts); RUN_TEST(expired_boundary);
    RUN_TEST(probe_replaces_window); RUN_TEST(boot_guard); RUN_TEST(modes_and_fault);
    RUN_TEST(retry_once); RUN_TEST(retry_session_guard); RUN_TEST(strict_command_json);
    RUN_TEST(command_size_and_probe); RUN_TEST(status_and_release_history); RUN_TEST(status_bounds_and_reboot);
    RUN_TEST(round_limit_retains_buffer); RUN_TEST(final_status_disables_new_execution);
}
