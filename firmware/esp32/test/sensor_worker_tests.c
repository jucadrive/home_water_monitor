#include "worker.h"
#include "session.h"
#include "esp_timer.h"
#include "unity.h"

typedef struct { bool ok; unsigned delay_ms, reads; } source;
static uint64_t clock_ms(void) { return esp_timer_get_time() / 1000; }
static bool read_adc(void *context, int16_t *out)
{
    source *s = context;
    s->reads++;
    if (s->delay_ms) vTaskDelay(pdMS_TO_TICKS(s->delay_ms));
    *out = 8000;
    return s->ok;
}
static void wait_idle(hwm_sensor_worker *w)
{
    uint64_t until = clock_ms() + 5000;
    while (hwm_worker_busy(w) && clock_ms() < until) vTaskDelay(1);
    TEST_ASSERT_FALSE(hwm_worker_busy(w));
}
static hwm_sensor_worker worker;
static source input;
static hwm_worker_result result;
static uint32_t active_generation;
static bool start(uint32_t generation, unsigned target, uint64_t at)
{
    bool accepted = hwm_worker_start(&worker, generation, target, at);
    if (accepted) active_generation = generation;
    return accepted;
}
void hwm_sensor_worker_test_cleanup(void)
{
    if (!worker.task) return;
    hwm_worker_cancel(&worker, active_generation);
    wait_idle(&worker);
    TEST_ASSERT_TRUE(hwm_worker_deinit(&worker));
}

static void worker_validation_and_cancel(void)
{
    input = (source){.ok = true};
    TEST_ASSERT_FALSE(hwm_worker_init(&worker, read_adc, &input, 0));
    TEST_ASSERT_FALSE(hwm_worker_init(&worker, NULL, &input, HWM_WORKER_CAPACITY));
    TEST_ASSERT_TRUE(hwm_worker_init(&worker, read_adc, &input, HWM_WORKER_CAPACITY));
    TEST_ASSERT_FALSE(start(0, 1, clock_ms()));
    TEST_ASSERT_FALSE(start(1, 61, clock_ms()));
    TEST_ASSERT_TRUE(start(1, 60, clock_ms()));
    TEST_ASSERT_FALSE(start(2, 1, clock_ms()));
    TEST_ASSERT_FALSE(hwm_worker_deinit(&worker));
    vTaskDelay(pdMS_TO_TICKS(100));
    hwm_worker_cancel(&worker, 1);
    wait_idle(&worker);
    TEST_ASSERT_TRUE(input.reads > 0 && input.reads < 25);
    TEST_ASSERT_FALSE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_TRUE(hwm_worker_deinit(&worker));
}
static void worker_delayed_consumer_keeps_actual_time(void)
{
    input = (source){.ok = true};
    TEST_ASSERT_TRUE(hwm_worker_init(&worker, read_adc, &input, HWM_WORKER_CAPACITY));
    uint64_t began = clock_ms();
    TEST_ASSERT_TRUE(start(3, 3, began));
    /* The real FreeRTOS producer keeps sampling while the consumer does no work for 3.5 seconds. */
    vTaskDelay(pdMS_TO_TICKS(3500));
    uint64_t processed = clock_ms();
    wait_idle(&worker);
    static hwm_session session;
    TEST_ASSERT_TRUE(hwm_init(&session, "esp32-bench"));
    TEST_ASSERT_TRUE(hwm_set_real_sensor(&session, true));
    TEST_ASSERT_TRUE(hwm_start(&session, "11111111-1111-4111-8111-111111111111", 3, began));
    uint64_t previous = began;
    for (unsigned seq = 1; seq <= 3; seq++) {
        TEST_ASSERT_TRUE(hwm_worker_receive(&worker, &result));
        TEST_ASSERT_EQUAL(3, result.generation); TEST_ASSERT_EQUAL(seq, result.seq);
        TEST_ASSERT_EQUAL(HWM_WORKER_OK, result.error);
        TEST_ASSERT_TRUE(result.at_ms - previous >= 1000 && result.at_ms - previous <= 1250);
        TEST_ASSERT_TRUE(result.at_ms < processed);
        TEST_ASSERT_TRUE(hwm_sample_append_observed(&session, result.at_ms, processed,
            result.value.tds_hundredths, result.value.voltage_hundredths));
        TEST_ASSERT_EQUAL(result.at_ms - began, session.samples[seq - 1].elapsed_ms);
        previous = result.at_ms;
    }
    TEST_ASSERT_EQUAL(75, input.reads);
    TEST_ASSERT_EQUAL(HWM_PREPARING, session.state);
    TEST_ASSERT_TRUE(session.deadline_ms == processed + HWM_PREPARE_MS);
    TEST_ASSERT_FALSE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_TRUE(hwm_worker_deinit(&worker));
}
static void worker_full_queue_and_next_generation(void)
{
    input = (source){.ok = true};
    TEST_ASSERT_TRUE(hwm_worker_init(&worker, read_adc, &input, 1));
    TEST_ASSERT_TRUE(start(10, 3, clock_ms()));
    wait_idle(&worker);
    TEST_ASSERT_FALSE(start(11, 1, clock_ms()));
    TEST_ASSERT_EQUAL(10, hwm_worker_take_failure(&worker));
    TEST_ASSERT_TRUE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_EQUAL(10, result.generation); TEST_ASSERT_EQUAL(1, result.seq);
    TEST_ASSERT_FALSE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_EQUAL(50, input.reads); /* failed enqueue stops sampling; never fabricates seq3 */
    input = (source){.ok = false};
    TEST_ASSERT_TRUE(start(11, 1, clock_ms()));
    wait_idle(&worker);
    TEST_ASSERT_TRUE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_EQUAL(11, result.generation); TEST_ASSERT_EQUAL(1, result.seq);
    TEST_ASSERT_EQUAL(HWM_WORKER_ADC_READ, result.error);
    TEST_ASSERT_EQUAL(0, hwm_worker_take_failure(&worker));
    TEST_ASSERT_TRUE(hwm_worker_deinit(&worker));
}
static void worker_late_read_remains_an_error(void)
{
    input = (source){.ok = true, .delay_ms = 170};
    TEST_ASSERT_TRUE(hwm_worker_init(&worker, read_adc, &input, HWM_WORKER_CAPACITY));
    TEST_ASSERT_TRUE(start(12, 1, clock_ms()));
    wait_idle(&worker);
    TEST_ASSERT_TRUE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_EQUAL(HWM_WORKER_LATE, result.error);
    TEST_ASSERT_EQUAL(1, input.reads);
    TEST_ASSERT_FALSE(hwm_worker_receive(&worker, &result));
    TEST_ASSERT_TRUE(hwm_worker_deinit(&worker));
}
void hwm_run_sensor_worker_tests(void)
{
    RUN_TEST(worker_validation_and_cancel);
    RUN_TEST(worker_delayed_consumer_keeps_actual_time);
    RUN_TEST(worker_full_queue_and_next_generation);
    RUN_TEST(worker_late_read_remains_an_error);
}
