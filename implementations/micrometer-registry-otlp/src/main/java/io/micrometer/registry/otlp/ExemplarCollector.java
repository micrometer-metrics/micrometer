package io.micrometer.registry.otlp;

import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.List;
import java.util.concurrent.TimeUnit;

interface ExemplarCollector {
    void offerMeasurement(double value);
    void offerDurationMeasurement(long nanos);
    List<Exemplar> collectAndReset();
    List<Exemplar> collectDurationAndReset(TimeUnit baseTimeUnit);
}
