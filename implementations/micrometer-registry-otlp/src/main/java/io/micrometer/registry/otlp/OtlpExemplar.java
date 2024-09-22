package io.micrometer.registry.otlp;

import com.google.protobuf.ByteString;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.concurrent.TimeUnit;

class OtlpExemplar {
    @Nullable
    private String traceId = null;
    @Nullable
    private String spanId = null;
    private long timeUnixNano = 0L;
    private double doubleValue = 0.0;
    private long durationInNano = 0L;

    synchronized void offerMeasurement(String traceId, String spanId, long timeUnixNano, double value) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.timeUnixNano = timeUnixNano;
        this.doubleValue = value;
    }

    synchronized void offerDurationMeasurement(String traceId, String spanId, long timeUnixNano, long durationInNano) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.timeUnixNano = timeUnixNano;
        this.durationInNano = durationInNano;
    }

    @Nullable
    synchronized Exemplar getAndReset(@Nullable TimeUnit baseTimeUnit) {
        Exemplar exemplar = null;
        if (traceId != null && spanId != null) {
            exemplar = Exemplar.newBuilder()
                .setTraceId(ByteString.fromHex(traceId))
                .setSpanId(ByteString.fromHex(spanId))
                .setTimeUnixNano(timeUnixNano)
                .setAsDouble(baseTimeUnit == null ? doubleValue : TimeUtils.nanosToUnit(durationInNano, baseTimeUnit))
                .build();
        }

        this.traceId = null;
        this.spanId = null;
        this.timeUnixNano = 0L;
        this.doubleValue = 0.0;
        this.durationInNano = 0L;

        return exemplar;
    }
}
