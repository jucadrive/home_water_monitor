#include "tds_sensor.h"
#include <string.h>

bool hwm_tds_value(const int16_t raw[HWM_SENSOR_WINDOW], hwm_sensor_value *out)
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
    if (numerator < 0 || numerator > 1000 * denominator) return false;
    *out = (hwm_sensor_value){
        .voltage_hundredths = (int32_t)((n * 25 + 1) / 2),
        .tds_hundredths = (int32_t)((numerator + denominator / 200) / (denominator / 100))
    };
    return true;
}
