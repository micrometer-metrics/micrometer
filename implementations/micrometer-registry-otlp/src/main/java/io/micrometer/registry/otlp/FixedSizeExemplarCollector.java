package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class FixedSizeExemplarCollector implements ExemplarCollector {
    private final Clock clock;
    private final SpanContextProvider spanContextProvider;
    private final CellSelector cellSelector;
    private final OtlpExemplar[] exemplars;
    private volatile boolean hasMeasurements = false;

    FixedSizeExemplarCollector(Clock clock, SpanContextProvider spanContextProvider, CellSelector cellSelector, int size) {
        this.clock = clock;
        this.spanContextProvider = spanContextProvider;
        this.cellSelector = cellSelector;
        this.exemplars = new OtlpExemplar[size];
        for (int i = 0; i < size; i++) {
            this.exemplars[i] = new OtlpExemplar();
        }
    }

    @Override
    public void offerMeasurement(double value) {
        SpanContextProvider.SpanContext spanContext = spanContextProvider.gerCurrentSpan();
        if (spanContext != null && spanContext.isSpanSampled()) {
            int index = cellSelector.cellIndexFor(value);
            if (index != -1) {
                long timeUnixNano = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());

                this.exemplars[index].offerMeasurement(spanContext.getTraceId(), spanContext.getSpanId(), timeUnixNano, value);
                this.hasMeasurements = true;
            }
        }
    }

    @Override
    public void offerDurationMeasurement(long nanos) {
        SpanContextProvider.SpanContext spanContext = spanContextProvider.gerCurrentSpan();
        if (spanContext != null && spanContext.isSpanSampled()) {
            int index = cellSelector.cellIndexFor(nanos);
            if (index != -1) {
                long timeUnixNano = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());

                this.exemplars[index].offerDurationMeasurement(spanContext.getTraceId(), spanContext.getSpanId(), timeUnixNano, nanos);
                this.hasMeasurements = true;
            }
        }
    }

    @Override
    public List<Exemplar> collectAndReset() {
        return internalCollectAndReset(null);
    }

    @Override
    public List<Exemplar> collectDurationAndReset(TimeUnit baseTimeUnit) {
        return internalCollectAndReset(baseTimeUnit);
    }

    private List<Exemplar> internalCollectAndReset(@Nullable TimeUnit baseTimeUnit) {
        if (!hasMeasurements) {
            return Collections.emptyList();
        }
        List<Exemplar> result = new ArrayList<>();
        for (OtlpExemplar otlpExemplar : exemplars) {
            Exemplar exemplar = otlpExemplar.getAndReset(baseTimeUnit);
            if (exemplar != null) {
                result.add(exemplar);
            }
        }

        this.cellSelector.reset();
        this.hasMeasurements = false;

        return Collections.unmodifiableList(result);
    }

    interface CellSelector {
        int cellIndexFor(double value);

        void reset();
    }
}
