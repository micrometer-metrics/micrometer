package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;

import java.util.HashMap;
import java.util.Map;

public class Placeholders {

    private final Map<String, String> metricNameMappings = new HashMap<>();

    private Placeholders() {
    }

    public static Placeholders withoutMappings() {
        return new Placeholders();
    }

    public Placeholders addMapping(String from, String to) {
        metricNameMappings.put(from, to);
        return this;
    }

    public Placeholders extendWith(Placeholders others) {
        metricNameMappings.putAll(others.metricNameMappings);
        return this;
    }

    public void bindTo(MeterRegistry... registries) {
        for (MeterRegistry registry : registries) {
            registry.config().meterFilter(new NameMappingFilter(metricNameMappings));
            registry.config().namingConvention(
                    new RemovePlaceholdersNamingConvention(registry.config().namingConvention())
            );
            if (registry instanceof DropwizardMeterRegistry) {
                ((DropwizardMeterRegistry) registry).moreConfig()
                        .nameMapper(new ResolvePlaceholdersNameMapper());
            }
        }
    }
}
