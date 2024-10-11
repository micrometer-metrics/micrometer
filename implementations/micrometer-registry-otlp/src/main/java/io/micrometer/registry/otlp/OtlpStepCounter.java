package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepCounter;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.Collections;
import java.util.List;

class OtlpStepCounter extends StepCounter implements OtlpExemplarMeter {
    @Nullable
    private final ExemplarCollector exemplarCollector;

    OtlpStepCounter(Id id, Clock clock, long stepMillis, @Nullable ExemplarCollectorFactory exemplarCollectorFactory) {
        super(id, clock, stepMillis);
        this.exemplarCollector = exemplarCollectorFactory == null ? null : exemplarCollectorFactory.fixedSize(1);
    }

    @Override
    public void increment(double amount) {
        super.increment(amount);
        if (exemplarCollector != null) {
            exemplarCollector.offerMeasurement(amount);
        }
    }

    @Override
    public List<Exemplar> exemplars() {
        return exemplarCollector == null ? Collections.emptyList() : exemplarCollector.collectAndReset();
    }
}
