#include "tds_sensor.h"
#include "session.h"
#include "unity.h"
#include <string.h>

static int16_t raw[HWM_SENSOR_WINDOW];
static hwm_sensor_value value;
static unsigned reads;
static bool read_ok;
static bool fake_read(void *context, int16_t *out)
{
    reads++;
    *out = *(int16_t *)context;
    return read_ok;
}
static void fill(int16_t n) { for (unsigned i = 0; i < HWM_SENSOR_WINDOW; i++) raw[i] = n; }
static void zero_and_one_count(void)
{
    fill(0); TEST_ASSERT_TRUE(hwm_tds_value(raw, &value));
    TEST_ASSERT_EQUAL_INT32(0, value.voltage_hundredths); TEST_ASSERT_EQUAL_INT32(0, value.tds_hundredths);
    fill(1); TEST_ASSERT_TRUE(hwm_tds_value(raw, &value));
    TEST_ASSERT_EQUAL_INT32(13, value.voltage_hundredths);
    TEST_ASSERT_EQUAL_INT32(5, value.tds_hundredths);
}
static void manufacturer_vector_and_half_up(void)
{
    fill(8000); TEST_ASSERT_TRUE(hwm_tds_value(raw, &value));
    TEST_ASSERT_EQUAL_INT32(100000, value.voltage_hundredths);
    TEST_ASSERT_EQUAL_INT32(36748, value.tds_hundredths); /* 367.475ppm, not 367.47 */
    fill(16000); TEST_ASSERT_TRUE(hwm_tds_value(raw, &value));
    TEST_ASSERT_EQUAL_INT32(87935, value.tds_hundredths);
}
static void median_not_average(void)
{
    fill(8000); for (unsigned i = 0; i < 12; i++) raw[i] = 16000;
    TEST_ASSERT_TRUE(hwm_tds_value(raw, &value)); TEST_ASSERT_EQUAL_INT32(100000, value.voltage_hundredths);
    TEST_ASSERT_EQUAL_INT16(16000, raw[0]); /* source observations are not sorted in place */
}
static void input_and_ppm_ranges(void)
{
    fill(8000); raw[0] = -1; TEST_ASSERT_FALSE(hwm_tds_value(raw, &value));
    fill(8000); raw[0] = 18401; TEST_ASSERT_FALSE(hwm_tds_value(raw, &value));
    fill(18400); TEST_ASSERT_FALSE(hwm_tds_value(raw, &value)); /* <=2.3V but >1000ppm */
    fill(32767); TEST_ASSERT_FALSE(hwm_tds_value(raw, &value));
    TEST_ASSERT_FALSE(hwm_tds_value(NULL, &value)); TEST_ASSERT_FALSE(hwm_tds_value(raw, NULL));
}
static void one_window_and_new_session(void)
{
    hwm_acquisition a; int16_t n = 8000; reads = 0; read_ok = true;
    hwm_sensor_begin(&a, 100);
    TEST_ASSERT_EQUAL(HWM_SENSOR_PENDING, hwm_sensor_poll(&a, 139, fake_read, &n, &value));
    TEST_ASSERT_EQUAL(0, reads);
    for (unsigned i = 1; i < 25; i++)
        TEST_ASSERT_EQUAL(HWM_SENSOR_PENDING, hwm_sensor_poll(&a, 100 + i * 40, fake_read, &n, &value));
    TEST_ASSERT_EQUAL(HWM_SENSOR_SAMPLE, hwm_sensor_poll(&a, 1100, fake_read, &n, &value));
    TEST_ASSERT_EQUAL(25, reads); TEST_ASSERT_EQUAL(0, a.count);
    hwm_sensor_begin(&a, 2000); TEST_ASSERT_EQUAL(0, a.count); TEST_ASSERT_FALSE(a.failed);
    TEST_ASSERT_EQUAL(HWM_SENSOR_PENDING, hwm_sensor_poll(&a, 2039, fake_read, &n, &value));
}
static void failure_and_lateness(void)
{
    hwm_acquisition a; int16_t n = 8000; read_ok = false; reads = 0;
    hwm_sensor_begin(&a, 0);
    TEST_ASSERT_EQUAL(HWM_SENSOR_ERROR, hwm_sensor_poll(&a, 40, fake_read, &n, &value));
    TEST_ASSERT_EQUAL_STRING("adc_read", a.failure_reason);
    read_ok = true;
    TEST_ASSERT_EQUAL(HWM_SENSOR_ERROR, hwm_sensor_poll(&a, 80, fake_read, &n, &value));
    TEST_ASSERT_EQUAL(1, reads); /* no silent resume after losing a reading */
    hwm_sensor_begin(&a, 0);
    TEST_ASSERT_EQUAL(HWM_SENSOR_ERROR, hwm_sensor_poll(&a, 141, fake_read, &n, &value));
    TEST_ASSERT_EQUAL_STRING("late", a.failure_reason);
    TEST_ASSERT_EQUAL(1, reads);
    hwm_sensor_begin(&a, 0); n = -1;
    TEST_ASSERT_EQUAL(HWM_SENSOR_ERROR, hwm_sensor_poll(&a, 40, fake_read, &n, &value));
    TEST_ASSERT_EQUAL_STRING("adc_range", a.failure_reason);
}
static hwm_session s;
static const char *session_id = "12345678-1234-4234-8234-123456789abc";
static void session_source_and_frozen_retry(void)
{
    TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_set_real_sensor(&s, true));
    TEST_ASSERT_TRUE(hwm_start(&s, session_id, 60, 0));
    TEST_ASSERT_FALSE(hwm_set_real_sensor(&s, false));
    TEST_ASSERT_FALSE(hwm_sample_simulated(&s, 1000));
    TEST_ASSERT_TRUE(hwm_sample_append(&s, 1000, 36748, 100000));
    TEST_ASSERT_TRUE(hwm_finish_incomplete(&s, 1100));
    TEST_ASSERT_NOT_NULL(strstr(s.payload, HWM_SENSOR_CALIBRATION));
    TEST_ASSERT_NOT_NULL(strstr(s.payload, "\"tdsPpm\":367.48"));
    TEST_ASSERT_NOT_NULL(strstr(s.payload, "\"rawVoltageMv\":1000.00"));
    char frozen[1024]; TEST_ASSERT_LESS_THAN(sizeof(frozen), s.payload_len);
    strcpy(frozen, s.payload); s.state = HWM_MANUAL_WAIT;
    TEST_ASSERT_TRUE(hwm_retry(&s, session_id, 1200));
    TEST_ASSERT_EQUAL_STRING(frozen, s.payload);
    TEST_ASSERT_FALSE(hwm_sample_append(&s, 2000, 1, 1));
}
static void incomplete_prefix_counts(void)
{
    const unsigned sizes[] = {0, 1, 59};
    for (unsigned k = 0; k < 3; k++) {
        unsigned n = sizes[k];
        TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
        TEST_ASSERT_TRUE(hwm_set_real_sensor(&s, true));
        TEST_ASSERT_TRUE(hwm_start(&s, session_id, 60, 0));
        for (unsigned i = 1; i <= n; i++) TEST_ASSERT_TRUE(hwm_sample_append(&s, i * 1000, 100, 200));
        TEST_ASSERT_TRUE(hwm_finish_incomplete(&s, n * 1000 + 1));
        TEST_ASSERT_EQUAL(n, s.count); TEST_ASSERT_TRUE(s.finalized);
        hwm_confirmation c = {.count = n, .normal = false};
        strcpy(c.device_id, s.device_id); strcpy(c.session_id, s.session_id);
        TEST_ASSERT_TRUE(hwm_release(&s, &c)); TEST_ASSERT_TRUE(s.real_sensor);
        TEST_ASSERT_EQUAL(HWM_IDLE, s.state); TEST_ASSERT_EQUAL(0, s.count);
    }
}
static void real_normal_and_warmup(void)
{
    TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_set_real_sensor(&s, true));
    TEST_ASSERT_TRUE(hwm_start(&s, session_id, 60, 0));
    TEST_ASSERT_FALSE(hwm_sample_append(&s, 999, 100, 100));
    TEST_ASSERT_FALSE(hwm_sample_append(&s, 1000, -1, 100));
    TEST_ASSERT_FALSE(hwm_sample_append(&s, 1000, 100001, 100));
    TEST_ASSERT_FALSE(hwm_sample_append(&s, 1000, 100, 230001));
    TEST_ASSERT_EQUAL(0, s.count);
    for (unsigned i = 1; i <= 60; i++) TEST_ASSERT_TRUE(hwm_sample_append(&s, i * 1000, 36748, 100000));
    TEST_ASSERT_TRUE(s.finalized); TEST_ASSERT_EQUAL(60, s.count);
    for (unsigned i = 0; i < 60; i++) {
        TEST_ASSERT_EQUAL(i + 1, s.samples[i].seq);
        TEST_ASSERT_EQUAL(i < 10, s.samples[i].warmup);
    }
    TEST_ASSERT_FALSE(hwm_finish_incomplete(&s, 60001));
}
static void varying_io_duration_preserves_sample_interval(void)
{
    hwm_acquisition a; int16_t n = 8000; read_ok = true;
    TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_set_real_sensor(&s, true));
    TEST_ASSERT_TRUE(hwm_start(&s, session_id, 60, 0));
    hwm_sensor_begin(&a, 0);
    uint64_t previous = 0;
    for (unsigned sample = 0; sample < 60; sample++) {
        uint64_t observed = 0;
        for (unsigned i = 0; i < 25; i++) {
            observed = a.next_read;
            TEST_ASSERT_EQUAL(i == 24 ? HWM_SENSOR_SAMPLE : HWM_SENSOR_PENDING,
                hwm_sensor_poll(&a, observed, fake_read, &n, &value));
        }
        /* A shorter final I/O must not make the next append 1ms early. */
        uint64_t completed = observed + (sample % 2 ? 1 : 2);
        TEST_ASSERT_TRUE(completed - previous >= 1000);
        TEST_ASSERT_TRUE(hwm_sample_append(&s, completed, value.tds_hundredths, value.voltage_hundredths));
        hwm_sensor_begin(&a, completed);
        previous = completed;
    }
    TEST_ASSERT_EQUAL(60, s.count); TEST_ASSERT_TRUE(s.finalized);
}
static void delayed_append_uses_fresh_transmission_deadline(void)
{
    TEST_ASSERT_TRUE(hwm_init(&s, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_start(&s, session_id, 1, 100));
    TEST_ASSERT_FALSE(hwm_sample_append_observed(&s, 1100, 1099, 100, 100));
    TEST_ASSERT_EQUAL(0, s.count);
    TEST_ASSERT_TRUE(hwm_sample_append_observed(&s, 1100, 30000, 100, 100));
    TEST_ASSERT_EQUAL(1000, s.samples[0].elapsed_ms);
    TEST_ASSERT_TRUE(s.deadline_ms == 30000 + HWM_PREPARE_MS);
    hwm_tick(&s, 30000, 0);
    TEST_ASSERT_EQUAL(HWM_PREPARING, s.state);
}
void hwm_run_sensor_tests(void)
{
    RUN_TEST(zero_and_one_count); RUN_TEST(manufacturer_vector_and_half_up);
    RUN_TEST(median_not_average); RUN_TEST(input_and_ppm_ranges);
    RUN_TEST(one_window_and_new_session); RUN_TEST(failure_and_lateness);
    RUN_TEST(session_source_and_frozen_retry); RUN_TEST(incomplete_prefix_counts); RUN_TEST(real_normal_and_warmup);
    RUN_TEST(varying_io_duration_preserves_sample_interval);
    RUN_TEST(delayed_append_uses_fresh_transmission_deadline);
}
