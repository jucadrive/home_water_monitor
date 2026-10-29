package com.juca.homewatermonitorbackend.session.application;

import java.math.BigDecimal;
import java.util.Set;

// Validate acquisition provenance before any persistence or statistics calculation.
final class TemperatureInput {
    static final String VERSION = "sen0244-voltage-atc-v1";
    private TemperatureInput() { }
    static void validate(SaveNormalTdsSessionCommand c) {
        boolean v2 = c.temperatureSource() != null;
        if (v2) {
            require(Set.of("DS18B20", "SIMULATED").contains(c.temperatureSource()));
            require(VERSION.equals(c.compensationVersion()) && c.temperatureAssumedC() == null);
            require(Set.of("sen0244-uncalibrated-v1", "simulated-v1").contains(c.calibrationVersion()));
        } else require(c.compensationVersion() == null);
        for (var m : c.measurements()) {
            if (!v2) { require(m.temperatureC() == null && m.temperatureElapsedMs() == null); continue; }
            BigDecimal t = m.temperatureC(); Integer at = m.temperatureElapsedMs();
            require(t != null && t.stripTrailingZeros().scale() <= 4 && t.signum() >= 0 && t.compareTo(new BigDecimal("50")) <= 0);
            require(at != null && at >= 0 && at <= m.elapsedMs() && (long)m.elapsedMs() - at <= 2000);
            require(m.rawVoltageMv() != null && m.rawVoltageMv().signum() >= 0 && m.rawVoltageMv().compareTo(new BigDecimal("2300")) <= 0);
            require(m.tdsPpm() != null && m.tdsPpm().signum() >= 0 && m.tdsPpm().compareTo(new BigDecimal("1000")) <= 0);
        }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("TEMPERATURE_INPUT_INVALID"); }
}
