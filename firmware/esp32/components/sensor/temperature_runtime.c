#include "temperature_runtime.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "esp_timer.h"
#include "onewire_bus.h"
#include "ds18b20.h"
#include <math.h>
#include <stdatomic.h>

static StaticQueue_t queue_control;
static uint8_t queue_storage[sizeof(hwm_temperature)];
static QueueHandle_t latest;
static StaticTask_t task_control;
static StackType_t stack[4096 / sizeof(StackType_t)];
static atomic_bool failed;
static unsigned selected_mode;
static onewire_bus_handle_t bus;
static ds18b20_device_handle_t device;
static uint64_t clock_ms(void) { return esp_timer_get_time() / 1000; }

static bool open_sensor(void)
{
    /* One externally powered sensor, external 4.7k pull-up to 3.3V. No parasite power. */
    onewire_bus_config_t config = {.bus_gpio_num = 27, .flags.en_pull_up = false};
    onewire_bus_rmt_config_t rmt = {.max_rx_bytes = 10};
    if (onewire_new_bus_rmt(&config, &rmt, &bus) != ESP_OK) return false;
    onewire_device_iter_handle_t iter = NULL;
    onewire_device_t found;
    ds18b20_config_t sensor_config = {};
    if (onewire_new_device_iter(bus, &iter) != ESP_OK) return false;
    esp_err_t first = onewire_device_iter_get_next(iter, &found);
    bool ok = first == ESP_OK && ds18b20_new_device_from_enumeration(&found, &sensor_config, &device) == ESP_OK;
    /* Ambiguous multiple sensors are not silently selected by enumeration order. */
    if (ok) ok = onewire_device_iter_get_next(iter, &found) == ESP_ERR_NOT_FOUND;
    onewire_del_device_iter(iter);
    return ok && ds18b20_set_resolution(device, DS18B20_RESOLUTION_12B) == ESP_OK;
}
static void run(void *arg)
{
    (void)arg;
    bool ok = selected_mode == 1 || open_sensor();
    while (ok) {
        float value = 25.0f;
        if (selected_mode == 2) {
            /* The SDK driver waits for conversion here, outside ADC/control/MQTT tasks.
               get_temperature validates scratchpad CRC; never read before triggering. */
            ok = ds18b20_trigger_temperature_conversion(device) == ESP_OK
                && ds18b20_get_temperature(device, &value) == ESP_OK;
        }
        hwm_temperature t = {.valid = ok && isfinite(value) && value >= 0 && value <= 50,
            .ten_thousandths = ok && isfinite(value) && value >= 0 && value <= 50 ? (int32_t)lroundf(value * 10000) : 0,
            .at_ms = clock_ms()};
        xQueueOverwrite(latest, &t);
        if (!t.valid) break;
        vTaskDelay(pdMS_TO_TICKS(selected_mode == 1 ? 250 : 50));
    }
    hwm_temperature invalid = {0};
    xQueueOverwrite(latest, &invalid);
    atomic_store(&failed, true);
    /* Failure is latched for this boot. No stale snapshot is reused during recovery. */
    vTaskSuspend(NULL);
}
bool hwm_temperature_init(unsigned mode)
{
    if (mode == 0) return true;
    if (mode > 2 || latest) return false;
    selected_mode = mode;
    atomic_init(&failed, false);
    latest = xQueueCreateStatic(1, sizeof(hwm_temperature), queue_storage, &queue_control);
    return latest && xTaskCreateStaticPinnedToCore(run, "hwm_temperature", sizeof(stack), NULL, 4,
        stack, &task_control, xPortGetCoreID()) != NULL;
}
bool hwm_temperature_latest(hwm_temperature *out)
{
    return out && latest && !atomic_load(&failed) && xQueuePeek(latest, out, 0) == pdTRUE && out->valid;
}
bool hwm_temperature_faulted(void) { return atomic_load(&failed); }
