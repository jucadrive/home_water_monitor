#include "tds_sensor.h"
#include <string.h>

void hwm_sensor_begin(hwm_acquisition *a, uint64_t now)
{
    memset(a, 0, sizeof(*a));
    a->active = true;
    a->next_read = now + 40;
}
hwm_sensor_result hwm_sensor_poll(hwm_acquisition *a, uint64_t now,
    hwm_adc_read read, void *context, hwm_sensor_value *out)
{
    if (!a || !a->active || a->failed || !read || !out) return HWM_SENSOR_ERROR;
    if (now < a->next_read) return HWM_SENSOR_PENDING;
    int16_t raw;
    /* Never fabricate catch-up samples or accept a stale register as a missed observation. */
    if (now - a->next_read > 100) a->failure_reason = "late";
    else if (!read(context, &raw)) a->failure_reason = "adc_read";
    else if (raw < 0 || raw > 18400) a->failure_reason = "adc_range";
    if (a->failure_reason) {
        a->failed = true;
        return HWM_SENSOR_ERROR;
    }
    a->window[a->count++] = raw;
    a->next_read = now + 40;
    if (a->count < HWM_SENSOR_WINDOW) return HWM_SENSOR_PENDING;
    a->count = 0;
    if (!hwm_tds_value(a->window, out)) {
        a->failure_reason = "conversion"; a->failed = true; return HWM_SENSOR_ERROR;
    }
    return HWM_SENSOR_SAMPLE;
}
