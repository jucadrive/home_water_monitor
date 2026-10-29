#include "tds_sensor.h"
#include <string.h>

static bool value(const int16_t raw[HWM_SENSOR_WINDOW], hwm_sensor_value *out, bool limited)
{
    if (!raw || !out) return false;
    int16_t sorted[HWM_SENSOR_WINDOW];
    memcpy(sorted, raw, sizeof(sorted));
    for (unsigned i = 0; i < HWM_SENSOR_WINDOW; i++) {
        /* Reject invalid observations before filtering; a median must not hide overrange input. */
        if (sorted[i] < 0 || sorted[i] > 18400) return false;
        int16_t value = sorted[i];
        unsigned j = i;
        while (j && sorted[j - 1] > value) { sorted[j] = sorted[j - 1]; j--; }
        sorted[j] = value;
    }
    int64_t n = sorted[HWM_SENSOR_WINDOW / 2];
    /* SEN0244 manufacturer's voltage polynomial, K=1, TDS factor=0.5, assumed T=25C.
       No Arduino/EEPROM implementation is imported; this is uncalibrated estimation. */
    /* Exact rational evaluation at volts=n/8000 avoids binary rounding at x.xx5.
       Largest intermediate at n=18400 is below INT64_MAX. */
    int64_t numerator = ((13342 * n - 25586LL * 8000) * n + 85739LL * 64000000) * n;
    const int64_t denominator = 102400000000000LL;
    if (numerator < 0 || (limited && numerator > 1000 * denominator)) return false;
    *out = (hwm_sensor_value){
        .adc_median = (int16_t)n,
        .voltage_hundredths = (int32_t)((n * 25 + 1) / 2),
        .tds_hundredths = (int32_t)((numerator + denominator / 200) / (denominator / 100))
    };
    return true;
}

bool hwm_tds_value(const int16_t raw[HWM_SENSOR_WINDOW], hwm_sensor_value *out) { return value(raw, out, true); }
bool hwm_tds_voltage(const int16_t raw[HWM_SENSOR_WINDOW], hwm_sensor_value *out) { return value(raw, out, false); }

bool hwm_tds_compensate(hwm_sensor_value *v, int32_t temperature)
{
    if (!v || temperature < 0 || temperature > 500000 || temperature % 625
        || v->adc_median < 0 || v->adc_median > 18400) return false;
    /* DS18B20 12-bit steps are 1/16 C (=625 ten-thousandths).
       For k=T*16, V25=n/[10*(400+k)]. This keeps exact rational HALF_UP,
       including the legacy 25 C result, without intermediate floating-point rounding.
       n<=18400, d<=12000: all intermediates, including 200000*d^3, fit INT64_MAX. */
    int64_t n = v->adc_median, d = 10LL * (400 + temperature / 625);
    int64_t cube = d * d * d;
    int64_t numerator = ((13342 * n - 25586 * d) * n + 85739 * d * d) * n;
    if (numerator < 0 || numerator > 200000 * cube) return false;
    v->tds_hundredths = (int32_t)((numerator + cube) / (2 * cube));
    return true;
}
