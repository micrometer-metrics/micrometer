package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.Map;

class NameMappingFilter implements MeterFilter {

    private final Map<String, String> mappings;

    NameMappingFilter(Map<String, String> mappings) {
        this.mappings = mappings;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        String mappedName = mappings.get(id.getName());

        if (mappedName == null) {
            return id;
        }

        return id.withName(mappedName);
    }
}
