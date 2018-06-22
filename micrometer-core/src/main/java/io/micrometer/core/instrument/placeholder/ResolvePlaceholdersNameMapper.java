package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

class ResolvePlaceholdersNameMapper implements HierarchicalNameMapper {

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        String name = id.getName();

        for (Tag tag : id.getTags()) {
            name = name.replace("{" + tag.getKey() + "}", tag.getValue());
        }

        if (name.contains("{") || name.contains("}")) {
            throw new IllegalArgumentException("Some placeholders in the metric name do not have a matching tag! " +
                    "Metric name: " + id.getName() + ", after resolving with tags provided: " + name );
        }

        return name;
    }
}
