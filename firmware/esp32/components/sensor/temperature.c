#include "temperature.h"

bool hwm_temperature_valid(const hwm_temperature *t, uint64_t now, uint64_t started)
{
    return t && t->valid && t->ten_thousandths >= 0 && t->ten_thousandths <= 500000
        && t->at_ms >= started && t->at_ms <= now && now - t->at_ms <= 2000;
}
