#ifndef HWM_TEMPERATURE_RUNTIME_H
#define HWM_TEMPERATURE_RUNTIME_H
#include "temperature.h"

/* Boot-lifetime provider: 0=legacy assumed, 1=explicit simulation, 2=DS18B20. */
bool hwm_temperature_init(unsigned mode);
bool hwm_temperature_latest(hwm_temperature *out);
bool hwm_temperature_faulted(void);
#endif
