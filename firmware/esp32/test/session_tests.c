#include "session.h"
#include "control.h"
#include "frame.h"
#include "unity.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

static hwm_session session;
static hwm_frame frame;
static char copy[HWM_MAX_PAYLOAD + 2];
static const char *uuid = "12345678-1234-4234-8234-123456789abc";
static const char *other = "12345678-1234-4234-8234-123456789abd";
static const char *normal = "{\"schemaVersion\":1,\"eventType\":\"SESSION_PRESERVED\",\"deviceId\":\"esp32-bench\","
    "\"sessionId\":\"12345678-1234-4234-8234-123456789abc\",\"preservationResult\":\"NORMAL\",\"preservedSampleCount\":60,\"comparison\":null}";

void setUp(void) { TEST_ASSERT_TRUE(hwm_init(&session, "esp32-bench")); memset(&frame, 0, sizeof(frame)); }
void tearDown(void) {}
static void filled(unsigned count)
{
    TEST_ASSERT_TRUE(hwm_start(&session, uuid, count, 0));
    for (unsigned i = 1; i <= count; i++) TEST_ASSERT_TRUE(hwm_sample_simulated(&session, i * 1000));
    TEST_ASSERT_TRUE(session.finalized);
}
static hwm_confirmation matching(void)
{
    hwm_confirmation c = {.count = session.count, .normal = session.count == 60};
    strcpy(c.device_id, session.device_id);
    strcpy(c.session_id, session.session_id);
    return c;
}
static bool parse(const char *text) { hwm_confirmation c; return hwm_parse_confirmation(text, strlen(text), &c); }
static void replace(const char *from, const char *to)
{
    const char *at = strstr(normal, from);
    TEST_ASSERT_NOT_NULL(at);
    size_t prefix = (size_t)(at - normal);
    memcpy(copy, normal, prefix);
    strcpy(copy + prefix, to);
    strcat(copy, at + strlen(from));
}
static void control_commands(void)
{
    hwm_command c;
    TEST_ASSERT_TRUE(hwm_parse_command("STATUS", &c));
    TEST_ASSERT_EQUAL(HWM_COMMAND_STATUS, c.type);
    TEST_ASSERT_TRUE(hwm_parse_command("START 12345678-1234-4234-8234-123456789ABC", &c));
    TEST_ASSERT_EQUAL_STRING(uuid, c.session_id);
    TEST_ASSERT_TRUE(hwm_parse_command("RETRY 12345678-1234-4234-8234-123456789abc", &c));
    TEST_ASSERT_EQUAL(HWM_COMMAND_RETRY, c.type);
}
static void invalid_commands(void)
{
    hwm_command c;
    const char *bad[] = {"", "status", " STATUS", "STATUS ", "START", "START 1", "START 12345678-1234-4234-8234-123456789abg", "RETRY 12345678-1234-4234-8234-123456789abc x"};
    for (unsigned i = 0; i < sizeof(bad)/sizeof(bad[0]); i++) TEST_ASSERT_FALSE(hwm_parse_command(bad[i], &c));
    TEST_ASSERT_FALSE(hwm_parse_command(NULL, &c));
}
static void device_identifiers(void)
{
    const char *bad[] = {"", "_bad", "bad/", "bad+", "bad#", "a\n"};
    for (unsigned i = 0; i < sizeof(bad)/sizeof(bad[0]); i++) TEST_ASSERT_FALSE(hwm_init(&session, bad[i]));
    memset(copy, 'a', 64); copy[64] = 0;
    TEST_ASSERT_TRUE(hwm_init(&session, copy)); copy[64] = 'a'; copy[65] = 0;
    TEST_ASSERT_FALSE(hwm_init(&session, copy));
}
static void final_normal_list(void)
{
    filled(60);
    TEST_ASSERT_EQUAL(HWM_PREPARING, session.state);
    TEST_ASSERT_EQUAL(60, session.count);
    for (unsigned i = 0; i < 60; i++) {
        TEST_ASSERT_EQUAL(i + 1, session.samples[i].seq);
        TEST_ASSERT_EQUAL((i + 1) * 1000, session.samples[i].elapsed_ms);
        TEST_ASSERT_EQUAL(i < 10, session.samples[i].warmup);
    }
    TEST_ASSERT_NOT_NULL(strstr(session.payload, "\"startedAt\":null"));
    TEST_ASSERT_NOT_NULL(strstr(session.payload, "\"tdsPpm\":200.01"));
    TEST_ASSERT_NOT_NULL(strstr(session.payload, "\"calibrationVersion\":\"simulated-v1\""));
    TEST_ASSERT_LESS_OR_EQUAL(HWM_MAX_PAYLOAD, session.payload_len);
    TEST_ASSERT_FALSE(hwm_sample_simulated(&session, 61000));
}
static void sample_timing(void)
{
    TEST_ASSERT_TRUE(hwm_start(&session, uuid, 1, 100));
    TEST_ASSERT_FALSE(hwm_sample_simulated(&session, 1099));
    TEST_ASSERT_TRUE(hwm_sample_simulated(&session, 1107));
    TEST_ASSERT_EQUAL(1007, session.samples[0].elapsed_ms);
}
static void empty_list(void) { filled(0); TEST_ASSERT_NOT_NULL(strstr(session.payload, "\"measurements\":[]")); }
static void incomplete_one(void) { filled(1); TEST_ASSERT_EQUAL(1, session.count); TEST_ASSERT_TRUE(session.samples[0].warmup); }
static void incomplete_59(void) { filled(59); hwm_confirmation c = matching(); TEST_ASSERT_TRUE(hwm_release(&session, &c)); }
static void serialization_capacity(void) { filled(60); TEST_ASSERT_EQUAL(0, hwm_serialize(&session, copy, 10)); }
static void start_guards(void)
{
    TEST_ASSERT_FALSE(hwm_start(&session, uuid, 61, 0));
    TEST_ASSERT_FALSE(hwm_start(&session, "invalid", 60, 0));
    filled(0); TEST_ASSERT_FALSE(hwm_start(&session, other, 60, 0));
    hwm_confirmation c = matching(); TEST_ASSERT_TRUE(hwm_release(&session, &c));
    TEST_ASSERT_FALSE(hwm_start(&session, uuid, 60, 0));
    TEST_ASSERT_TRUE(hwm_start(&session, other, 60, 0));
    TEST_ASSERT_FALSE(hwm_start(&session, uuid, 60, 0));
}
static void prepare_timeout(void)
{
    filled(0); hwm_tick(&session, 9999, 1000); TEST_ASSERT_EQUAL(HWM_PREPARING, session.state);
    hwm_tick(&session, 10000, 1000); TEST_ASSERT_EQUAL(HWM_BACKOFF, session.state);
    TEST_ASSERT_EQUAL(11000, session.deadline_ms);
    hwm_tick(&session, 10999, 0); TEST_ASSERT_EQUAL(1, session.attempt);
    hwm_tick(&session, 11000, 0); TEST_ASSERT_EQUAL(2, session.attempt);
}
static void five_attempts_and_frozen_buffer(void)
{
    filled(1); memcpy(copy, session.payload, session.payload_len + 1);
    for (unsigned i = 1; i <= 5; i++) {
        TEST_ASSERT_EQUAL(i, session.attempt);
        if (i < 5) TEST_ASSERT_EQUAL(1000U << (i - 1), hwm_retry_cap_ms(&session));
        hwm_tick(&session, session.deadline_ms, 0);
        if (i < 5) hwm_tick(&session, session.deadline_ms, 0);
    }
    TEST_ASSERT_EQUAL(HWM_MANUAL_WAIT, session.state);
    hwm_tick(&session, 99999999, 0); TEST_ASSERT_EQUAL(HWM_MANUAL_WAIT, session.state);
    TEST_ASSERT_EQUAL_STRING(copy, session.payload);
    TEST_ASSERT_EQUAL_STRING(uuid, session.session_id);
}
static void jitter_caps(void)
{
    filled(0);
    for (unsigned i = 1; i < 5; i++) {
        uint64_t now = session.deadline_ms;
        hwm_fail(&session, now, 99999);
        TEST_ASSERT_EQUAL(now + (1000U << (i - 1)), session.deadline_ms);
        hwm_tick(&session, session.deadline_ms, 0);
    }
}
static void puback_does_not_release(void)
{
    filled(1); TEST_ASSERT_TRUE(hwm_submitted(&session, 12, 1000));
    TEST_ASSERT_TRUE(hwm_puback(&session, session.generation, 12, true, 1010, 0));
    TEST_ASSERT_EQUAL(HWM_WAIT_CONFIRMATION, session.state);
    TEST_ASSERT_EQUAL(31010, session.deadline_ms); TEST_ASSERT_TRUE(session.finalized);
    hwm_tick(&session, 31009, 0); TEST_ASSERT_EQUAL(HWM_WAIT_CONFIRMATION, session.state);
    hwm_tick(&session, 31010, 0); TEST_ASSERT_EQUAL(HWM_BACKOFF, session.state);
    TEST_ASSERT_EQUAL(1, session.count);
}
static void puback_timeout(void)
{
    filled(0); TEST_ASSERT_TRUE(hwm_submitted(&session, 7, 20));
    hwm_tick(&session, 10019, 0); TEST_ASSERT_EQUAL(HWM_WAIT_PUBACK, session.state);
    hwm_tick(&session, 10020, 0); TEST_ASSERT_EQUAL(HWM_BACKOFF, session.state);
}
static void puback_rejected_or_stale(void)
{
    filled(0); TEST_ASSERT_TRUE(hwm_submitted(&session, 7, 0));
    TEST_ASSERT_FALSE(hwm_puback(&session, session.generation - 1, 7, true, 0, 0));
    TEST_ASSERT_FALSE(hwm_puback(&session, session.generation, 8, true, 0, 0));
    TEST_ASSERT_TRUE(hwm_puback(&session, session.generation, 7, false, 0, 0));
    TEST_ASSERT_EQUAL(HWM_BACKOFF, session.state); TEST_ASSERT_TRUE(session.finalized);
}
static void late_puback_and_submission(void)
{
    filled(0); TEST_ASSERT_FALSE(hwm_submitted(&session, 1, HWM_PREPARE_MS));
    TEST_ASSERT_TRUE(hwm_submitted(&session, 1, 1));
    TEST_ASSERT_TRUE(hwm_puback(&session, session.generation, 1, true, 1 + HWM_PUBACK_MS, 0));
    TEST_ASSERT_EQUAL(HWM_BACKOFF, session.state); TEST_ASSERT_TRUE(session.finalized);
}
static void manual_retry_guards(void)
{
    filled(0); TEST_ASSERT_FALSE(hwm_retry(&session, uuid, 0));
    for (unsigned i = 0; i < 5; i++) { hwm_tick(&session, session.deadline_ms, 0); if (i < 4) hwm_tick(&session, session.deadline_ms, 0); }
    TEST_ASSERT_FALSE(hwm_retry(&session, other, 0));
    TEST_ASSERT_TRUE(hwm_retry(&session, uuid, 999999)); TEST_ASSERT_EQUAL(2, session.round);
    TEST_ASSERT_EQUAL(1, session.attempt); TEST_ASSERT_FALSE(hwm_retry(&session, uuid, 999999));
}
static void confirmation_all_retained_states(void)
{
    hwm_state states[] = {HWM_PREPARING,HWM_WAIT_PUBACK,HWM_WAIT_CONFIRMATION,HWM_BACKOFF,HWM_MANUAL_WAIT};
    for (unsigned i = 0; i < sizeof(states)/sizeof(states[0]); i++) {
        TEST_ASSERT_TRUE(hwm_init(&session, "esp32-bench")); filled(1); session.state = states[i];
        hwm_confirmation c = matching(); uint32_t old = session.generation;
        TEST_ASSERT_TRUE(hwm_release(&session, &c)); TEST_ASSERT_EQUAL(HWM_IDLE, session.state);
        TEST_ASSERT_EQUAL(0, session.count); TEST_ASSERT_EQUAL(0, session.payload_len);
        TEST_ASSERT_FALSE(hwm_release(&session, &c));
        TEST_ASSERT_FALSE(hwm_puback(&session, old, 7, true, 0, 0));
        hwm_tick(&session, 999999, 0); TEST_ASSERT_EQUAL(HWM_IDLE, session.state);
    }
}
static void confirmation_mismatch(void)
{
    filled(60); hwm_confirmation c = matching();
    c.count = 59; TEST_ASSERT_FALSE(hwm_release(&session, &c)); c.count = 60;
    c.normal = false; TEST_ASSERT_FALSE(hwm_release(&session, &c)); c.normal = true;
    strcpy(c.session_id, other); TEST_ASSERT_FALSE(hwm_release(&session, &c)); strcpy(c.session_id, uuid);
    strcpy(c.device_id, "foreign"); TEST_ASSERT_FALSE(hwm_release(&session, &c));
    TEST_ASSERT_EQUAL(60, session.count);
}
static void conflict_confirmation_releases(void)
{
    filled(60); hwm_confirmation c = matching(); c.has_comparison = true; c.identical = 59; c.conflicts = 1;
    TEST_ASSERT_TRUE(hwm_release(&session, &c));
}
static void confirmation_during_measurement(void)
{
    TEST_ASSERT_TRUE(hwm_start(&session, uuid, 60, 0)); hwm_confirmation c = matching();
    TEST_ASSERT_FALSE(hwm_release(&session, &c));
}
static void valid_json(void)
{
    TEST_ASSERT_TRUE(parse(normal));
    replace("60,", "6e1,"); TEST_ASSERT_TRUE(parse(copy));
    replace("60,", "60.0000000000000000000000000000000000000000000000000000000000000000000000,"); TEST_ASSERT_TRUE(parse(copy));
    replace("schemaVersion", "schema\\u0056ersion"); TEST_ASSERT_TRUE(parse(copy));
    replace("789abc", "789ABC"); TEST_ASSERT_TRUE(parse(copy));
}
static void duplicate_and_unknown_keys(void)
{
    replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schema\\u0056ersion\":1"); TEST_ASSERT_FALSE(parse(copy));
    replace("\"schemaVersion\":1", "\"extra\":1,\"schemaVersion\":1"); TEST_ASSERT_FALSE(parse(copy));
    replace("schemaVersion", "schemaVersion\\u0000extra"); TEST_ASSERT_FALSE(parse(copy));
}
static void invalid_json_numbers(void)
{
    const char *bad[] = {"60.", "060", "+60", "60e", "59.99999999999999999999999999999999999999", "6.0000000000000000000001e1", "60e99999", "60e-99999", "-60", "60.1", "61", "NaN", "null", "true", "\"60\""};
    for (unsigned i = 0; i < sizeof(bad)/sizeof(bad[0]); i++) {
        char number[128]; snprintf(number, sizeof(number), "%s,", bad[i]); replace("60,", number); TEST_ASSERT_FALSE(parse(copy));
    }
}
static void invalid_json_syntax(void)
{
    strcpy(copy, normal); strcat(copy, "{}"); TEST_ASSERT_FALSE(parse(copy));
    replace("null}", "null,}"); TEST_ASSERT_FALSE(parse(copy));
    replace("null}", "null/*comment*/}"); TEST_ASSERT_FALSE(parse(copy));
    replace("SESSION_PRESERVED", "SESSION_\nPRESERVED"); TEST_ASSERT_FALSE(parse(copy));
    replace("SESSION_PRESERVED", "SESSION_\\x50RESERVED"); TEST_ASSERT_FALSE(parse(copy));
    replace("SESSION_PRESERVED", "SESSION_\\uD800RESERVED"); TEST_ASSERT_FALSE(parse(copy));
    TEST_ASSERT_FALSE(hwm_parse_confirmation(normal, strlen(normal) - 1, &(hwm_confirmation){0}));
}
static void invalid_json_semantics(void)
{
    replace("\"NORMAL\"", "\"INCOMPLETE\""); TEST_ASSERT_FALSE(parse(copy));
    replace("60,", "59,"); TEST_ASSERT_FALSE(parse(copy));
    replace("\"schemaVersion\":1", "\"schemaVersion\":2"); TEST_ASSERT_FALSE(parse(copy));
    replace("SESSION_PRESERVED", "SESSION_RESULT"); TEST_ASSERT_FALSE(parse(copy));
    replace("null}", "{\"identicalCount\":59,\"conflictCount\":0}}"); TEST_ASSERT_FALSE(parse(copy));
    replace("null}", "{\"identicalCount\":59,\"conflictCount\":1}}"); TEST_ASSERT_TRUE(parse(copy));
    replace("null}", "{\"identicalCount\":59,\"conflictCount\":1,\"conflictCount\":1}}"); TEST_ASSERT_FALSE(parse(copy));
}
static void empty_confirmation(void)
{
    replace("\"NORMAL\",\"preservedSampleCount\":60", "\"INCOMPLETE\",\"preservedSampleCount\":0");
    hwm_confirmation c; TEST_ASSERT_TRUE(hwm_parse_confirmation(copy, strlen(copy), &c));
    filled(0); TEST_ASSERT_TRUE(hwm_release(&session, &c));
}
static void payload_byte_boundary(void)
{
    size_t n = strlen(normal); memcpy(copy, normal, n); memset(copy + n, ' ', HWM_MAX_PAYLOAD + 1 - n);
    hwm_confirmation c;
    TEST_ASSERT_TRUE(hwm_parse_confirmation(copy, HWM_MAX_PAYLOAD, &c));
    TEST_ASSERT_FALSE(hwm_parse_confirmation(copy, HWM_MAX_PAYLOAD + 1, &c));
    TEST_ASSERT_FALSE(hwm_parse_confirmation("", 0, &c));
    memcpy(copy, normal, n); copy[1] = (char)0xff; TEST_ASSERT_FALSE(hwm_parse_confirmation(copy, n, &c));
}
static void fragmented_frame(void)
{
    size_t n = strlen(normal);
    TEST_ASSERT_EQUAL(0, hwm_frame_feed(&frame, n, 0, normal, 17, true));
    TEST_ASSERT_EQUAL(1, hwm_frame_feed(&frame, n, 17, normal + 17, n - 17, false));
    TEST_ASSERT_TRUE(parse(frame.body));
}
static void frame_rejections(void)
{
    TEST_ASSERT_EQUAL(-1, hwm_frame_feed(&frame, 3, 0, "abc", 3, false));
    TEST_ASSERT_EQUAL(-1, hwm_frame_feed(&frame, HWM_MAX_PAYLOAD + 1, 0, "a", 1, true));
    TEST_ASSERT_EQUAL(-1, hwm_frame_feed(&frame, 2, 0, "abc", 3, true));
    TEST_ASSERT_EQUAL(0, hwm_frame_feed(&frame, 3, 0, "a", 1, true));
    TEST_ASSERT_EQUAL(-1, hwm_frame_feed(&frame, 3, 2, "b", 1, true));
    TEST_ASSERT_EQUAL(0, hwm_frame_feed(&frame, 3, 0, "a", 1, true));
    TEST_ASSERT_EQUAL(-1, hwm_frame_feed(&frame, 3, 0, "abc", 3, true));
    TEST_ASSERT_EQUAL(1, hwm_frame_feed(&frame, 3, 0, "abc", 3, true));
}

void app_main(void)
{
    UNITY_BEGIN();
    RUN_TEST(control_commands); RUN_TEST(invalid_commands); RUN_TEST(device_identifiers);
    RUN_TEST(final_normal_list); RUN_TEST(sample_timing); RUN_TEST(empty_list); RUN_TEST(incomplete_one); RUN_TEST(incomplete_59);
    RUN_TEST(serialization_capacity); RUN_TEST(start_guards); RUN_TEST(prepare_timeout); RUN_TEST(five_attempts_and_frozen_buffer);
    RUN_TEST(jitter_caps); RUN_TEST(puback_does_not_release); RUN_TEST(puback_timeout); RUN_TEST(puback_rejected_or_stale);
    RUN_TEST(late_puback_and_submission);
    RUN_TEST(manual_retry_guards); RUN_TEST(confirmation_all_retained_states); RUN_TEST(confirmation_mismatch);
    RUN_TEST(conflict_confirmation_releases); RUN_TEST(confirmation_during_measurement); RUN_TEST(valid_json);
    RUN_TEST(duplicate_and_unknown_keys); RUN_TEST(invalid_json_numbers); RUN_TEST(invalid_json_syntax);
    RUN_TEST(invalid_json_semantics); RUN_TEST(empty_confirmation); RUN_TEST(payload_byte_boundary);
    RUN_TEST(fragmented_frame); RUN_TEST(frame_rejections);
    int failed = UNITY_END();
    while (1) {
        printf("HWM_TEST_RESULT tests=%u failed=%d ignored=%u\n", Unity.NumberOfTests, failed, Unity.TestIgnores);
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
