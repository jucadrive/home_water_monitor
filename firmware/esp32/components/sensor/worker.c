#include "worker.h"
#include "esp_timer.h"
#include <string.h>

static uint64_t clock_ms(void) { return esp_timer_get_time() / 1000; }
const char *hwm_worker_error_name(hwm_worker_error error)
{
    static const char *names[] = {"none", "late", "adc_read", "adc_range", "conversion", "state", "queue"};
    return (unsigned)error <= HWM_WORKER_QUEUE ? names[error] : "state";
}
static hwm_worker_error acquisition_error(const char *reason)
{
    for (unsigned i = HWM_WORKER_LATE; i <= HWM_WORKER_CONVERSION; i++)
        if (reason && !strcmp(reason, hwm_worker_error_name(i))) return i;
    return HWM_WORKER_STATE;
}
static void acquire(hwm_sensor_worker *w, const hwm_worker_request *request)
{
    hwm_acquisition acquisition;
    hwm_sensor_begin(&acquisition, request->started_ms);
    for (unsigned seq = 1; seq <= request->target;) {
        if (atomic_load(&w->cancelled_generation) == request->generation) return;
        hwm_worker_result result = {.generation = request->generation, .seq = seq};
        hwm_sensor_result state = hwm_sensor_poll(&acquisition, clock_ms(), w->read, w->context, &result.value);
        result.at_ms = clock_ms();
        if (state == HWM_SENSOR_PENDING) { vTaskDelay(1); continue; }
        result.window_count = acquisition.count;
        if (state == HWM_SENSOR_ERROR) result.error = acquisition_error(acquisition.failure_reason);
        /* A full queue terminates this job. A separate atomic signal cannot be lost in that same full queue. */
        if (xQueueSend(w->results, &result, 0) != pdTRUE) {
            atomic_store(&w->failed_generation, request->generation);
            return;
        }
        if (state == HWM_SENSOR_ERROR) return;
        hwm_sensor_begin(&acquisition, result.at_ms);
        seq++;
    }
}
static void run(void *arg)
{
    hwm_sensor_worker *w = arg;
    hwm_worker_request request;
    while (1) {
        if (xQueueReceive(w->requests, &request, portMAX_DELAY) == pdTRUE) {
            acquire(w, &request);
            atomic_store(&w->busy, false);
        }
    }
}
bool hwm_worker_init(hwm_sensor_worker *w, hwm_adc_read read, void *context, unsigned capacity)
{
    if (!w || w->task || !read || !capacity || capacity > HWM_WORKER_CAPACITY) return false;
    w->read = read; w->context = context;
    w->owner_core = xPortGetCoreID();
    atomic_init(&w->busy, false); atomic_init(&w->failed_generation, 0);
    atomic_init(&w->cancelled_generation, 0);
    w->requests = xQueueCreateStatic(1, sizeof(hwm_worker_request), w->request_storage, &w->request_control);
    w->results = xQueueCreateStatic(capacity, sizeof(hwm_worker_result), w->result_storage, &w->result_control);
    if (w->requests && w->results)
        w->task = xTaskCreateStaticPinnedToCore(run, "hwm_sensor", HWM_WORKER_STACK_BYTES,
            w, 6, w->stack, &w->task_control, w->owner_core);
    if (w->task) return true;
    if (w->requests) vQueueDelete(w->requests);
    if (w->results) vQueueDelete(w->results);
    w->requests = w->results = NULL;
    return false;
}
bool hwm_worker_start(hwm_sensor_worker *w, uint32_t generation, unsigned target, uint64_t started_ms)
{
    if (!w || !w->task || !generation || !target || target > 60 || started_ms > clock_ms()
        || uxQueueMessagesWaiting(w->results) || atomic_load(&w->failed_generation)) return false;
    bool expected = false;
    if (!atomic_compare_exchange_strong(&w->busy, &expected, true)) return false;
    atomic_store(&w->cancelled_generation, 0);
    hwm_worker_request request = {generation, target, started_ms};
    if (xQueueSend(w->requests, &request, 0) == pdTRUE) return true;
    atomic_store(&w->busy, false);
    return false;
}
bool hwm_worker_receive(hwm_sensor_worker *w, hwm_worker_result *out)
{
    return w && w->task && out && xQueueReceive(w->results, out, 0) == pdTRUE;
}
void hwm_worker_cancel(hwm_sensor_worker *w, uint32_t generation)
{
    if (w && w->task && generation) atomic_store(&w->cancelled_generation, generation);
}
uint32_t hwm_worker_take_failure(hwm_sensor_worker *w)
{
    return w && w->task ? atomic_exchange(&w->failed_generation, 0) : 0;
}
bool hwm_worker_busy(hwm_sensor_worker *w) { return w && w->task && atomic_load(&w->busy); }
bool hwm_worker_deinit(hwm_sensor_worker *w)
{
    if (!w || !w->task || hwm_worker_busy(w) || xPortGetCoreID() != w->owner_core) return false;
    vTaskDelete(w->task);
    vQueueDelete(w->requests); vQueueDelete(w->results);
    w->task = NULL; w->requests = w->results = NULL;
    return true;
}
