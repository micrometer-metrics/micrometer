package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;

public interface Placeholders {

    static void bindTo(MeterRegistry... registries) {
        for (MeterRegistry registry : registries) {
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
