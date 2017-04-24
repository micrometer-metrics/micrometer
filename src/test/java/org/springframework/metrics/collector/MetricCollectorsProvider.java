package org.springframework.metrics.collector;

import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ObjectArrayArguments;
import org.springframework.metrics.collector.spectator.*;
import org.springframework.metrics.collector.prometheus.*;

import java.util.stream.Stream;

class MetricCollectorsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> arguments(ContainerExtensionContext context) throws Exception {
        return Stream.of(new SpectatorMetricCollector(), new PrometheusMetricCollector())
                .map(ObjectArrayArguments::create);
    }
}
