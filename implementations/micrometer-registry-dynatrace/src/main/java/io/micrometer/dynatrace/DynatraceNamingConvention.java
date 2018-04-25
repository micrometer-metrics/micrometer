package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

public class DynatraceNamingConvention implements NamingConvention {

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return "custom:" + name;
    }
}
