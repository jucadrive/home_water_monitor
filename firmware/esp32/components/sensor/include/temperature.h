#ifndef HWM_TEMPERATURE_H
#define HWM_TEMPERATURE_H
#include <stdbool.h>
#include <stdint.h>

typedef struct { int32_t ten_thousandths; uint64_t at_ms; bool valid; } hwm_temperature;
/* A completed conversion in the acquisition interval, never a boot/default value. */
bool hwm_temperature_valid(const hwm_temperature *t, uint64_t now, uint64_t started);
#endif
