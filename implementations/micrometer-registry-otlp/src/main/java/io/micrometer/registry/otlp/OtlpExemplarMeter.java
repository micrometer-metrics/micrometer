package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.List;

interface OtlpExemplarMeter {
    @Nullable
    List<Exemplar> exemplars();
}
