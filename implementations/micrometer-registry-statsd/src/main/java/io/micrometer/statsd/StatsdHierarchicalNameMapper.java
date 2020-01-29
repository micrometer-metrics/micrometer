package io.micrometer.statsd;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class StatsdHierarchicalNameMapper implements HierarchicalNameMapper {

    private final String prefix;

    public StatsdHierarchicalNameMapper(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        final String prefixToAppend = prefix == null ? "" : prefix + ".";
        return prefixToAppend + DEFAULT.toHierarchicalName(id, convention);
    }
}