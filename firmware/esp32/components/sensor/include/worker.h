#ifndef HWM_SENSOR_WORKER_H
#define HWM_SENSOR_WORKER_H
#include "tds_sensor.h"
#include "temperature.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include <stdatomic.h>

#define HWM_WORKER_CAPACITY 61
#define HWM_WORKER_STACK_BYTES 4096
typedef enum {
    HWM_WORKER_OK, HWM_WORKER_LATE, HWM_WORKER_ADC_READ, HWM_WORKER_ADC_RANGE,
    HWM_WORKER_CONVERSION, HWM_WORKER_STATE, HWM_WORKER_QUEUE, HWM_WORKER_TEMPERATURE
} hwm_worker_error;
typedef struct { uint32_t generation; unsigned target; uint64_t started_ms; } hwm_worker_request;
/* Only values cross the task boundary; no session or SDK-owned pointers. */
typedef struct {
    uint32_t generation;
    unsigned seq, window_count;
    uint64_t at_ms;
    hwm_sensor_value value;
    hwm_temperature temperature;
    hwm_worker_error error;
} hwm_worker_result;
/* Zero-initialize; caller-owned storage must outlive the task. */
typedef struct {
    TaskHandle_t task;
    int owner_core;
    QueueHandle_t requests, results;
    StaticTask_t task_control;
    StaticQueue_t request_control, result_control;
    uint8_t request_storage[sizeof(hwm_worker_request)];
    uint8_t result_storage[HWM_WORKER_CAPACITY * sizeof(hwm_worker_result)];
    _Alignas(16) StackType_t stack[HWM_WORKER_STACK_BYTES / sizeof(StackType_t)];
    bool temperature_enabled;
    hwm_adc_read read;
    void *context;
    atomic_bool busy;
    atomic_uint failed_generation;
    atomic_uint cancelled_generation;
} hwm_sensor_worker;

bool hwm_worker_init(hwm_sensor_worker *w, hwm_adc_read read, void *context, unsigned capacity);
bool hwm_worker_start(hwm_sensor_worker *w, uint32_t generation, unsigned target, uint64_t started_ms);
bool hwm_worker_receive(hwm_sensor_worker *w, hwm_worker_result *out);
void hwm_worker_cancel(hwm_sensor_worker *w, uint32_t generation);
uint32_t hwm_worker_take_failure(hwm_sensor_worker *w);
bool hwm_worker_busy(hwm_sensor_worker *w);
/* Call from the same pinned owner core, while idle. Production keeps its worker for the boot lifetime. */
bool hwm_worker_deinit(hwm_sensor_worker *w);
const char *hwm_worker_error_name(hwm_worker_error error);
#endif
