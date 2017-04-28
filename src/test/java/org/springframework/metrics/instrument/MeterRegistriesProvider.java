package org.springframework.metrics.instrument;

import com.netflix.spectator.api.DefaultRegistry;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ObjectArrayArguments;
import org.springframework.metrics.instrument.spectator.*;
import org.springframework.metrics.instrument.prometheus.*;

import java.util.stream.Stream;

class MeterRegistriesProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> arguments(ContainerExtensionContext context) throws Exception {
        return Stream.of(
                new SpectatorMeterRegistry(new DefaultRegistry(), new MockClock()),
                new PrometheusMeterRegistry(new CollectorRegistry(true), new MockClock())
        ).map(ObjectArrayArguments::create);
    }
}