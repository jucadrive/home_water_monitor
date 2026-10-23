#ifndef HWM_ADS1115_H
#define HWM_ADS1115_H
#include <stdbool.h>
#include <stdint.h>

/* Fixed approved wiring: SDA21/SCL22, 3.3V, ADDR=GND (0x48), A0-GND. */
bool hwm_ads1115_init(void);
bool hwm_ads1115_read(void *context, int16_t *raw);
#endif
