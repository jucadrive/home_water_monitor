#ifndef HWM_TDS_SENSOR_H
#define HWM_TDS_SENSOR_H
#include <stdbool.h>
#include <stdint.h>

#define HWM_SENSOR_WINDOW 25
#define HWM_SENSOR_CALIBRATION "sen0244-uncalibrated-v1"
typedef enum { HWM_SENSOR_PENDING, HWM_SENSOR_SAMPLE, HWM_SENSOR_ERROR } hwm_sensor_result;
typedef struct { int32_t tds_hundredths, voltage_hundredths; } hwm_sensor_value;
typedef bool (*hwm_adc_read)(void *context, int16_t *raw);
typedef struct {
    int16_t window[HWM_SENSOR_WINDOW];
    unsigned count;
    uint64_t next_read;
    bool active, failed;
    const char *failure_reason;
} hwm_acquisition;

/* Unit: ADS1115 signed counts, A0-GND with FSR +/-4.096V (125 microvolts/count). */
bool hwm_tds_value(const int16_t raw[HWM_SENSOR_WINDOW], hwm_sensor_value *out);
void hwm_sensor_begin(hwm_acquisition *a, uint64_t now);
hwm_sensor_result hwm_sensor_poll(hwm_acquisition *a, uint64_t now,
    hwm_adc_read read, void *context, hwm_sensor_value *out);
#endif
