/*
 * Copyright 2025 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoubleExemplarData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

class OtlpExemplarSampler implements ExemplarSampler {

    private final ExemplarContextProvider exemplarContextProvider;

    private final Clock clock;

    private final Exemplars exemplars;

    private final DoubleUnaryOperator converter;

    OtlpExemplarSampler(ExemplarContextProvider exemplarContextProvider, Clock clock, OtlpConfig config,
            DoubleUnaryOperator converter) {
        this(exemplarContextProvider, clock, new Exemplars(clock, config.step().toMillis(), config.exemplarsSize()),
                converter);
    }

    OtlpExemplarSampler(ExemplarContextProvider exemplarContextProvider, Clock clock, OtlpConfig config, int size,
            DoubleUnaryOperator converter) {
        this(exemplarContextProvider, clock, new Exemplars(clock, config.step().toMillis(), size), converter);
    }

    OtlpExemplarSampler(ExemplarContextProvider exemplarContextProvider, Clock clock, OtlpConfig config,
            double[] buckets, DoubleUnaryOperator converter) {
        this(exemplarContextProvider, clock, new Exemplars(clock, config.step().toMillis(), buckets), converter);
    }

    private OtlpExemplarSampler(ExemplarContextProvider exemplarContextProvider, Clock clock, Exemplars exemplars,
            DoubleUnaryOperator converter) {
        this.exemplarContextProvider = exemplarContextProvider;
        this.clock = clock;
        this.exemplars = exemplars;
        this.converter = converter;
    }

    @Override
    public void sampleMeasurement(double measurement) {
        OtlpExemplarContext exemplarContext = exemplarContextProvider.getExemplarContext();
        if (exemplarContext != null) {
            exemplars.offer(measurement, converter, exemplarContext, clock);
        }
    }

    @Override
    public List<DoubleExemplarData> collectExemplars() {
        return exemplars.collect();
    }

    @Override
    public void close() {
        exemplars.close();
    }

    private static class Exemplars extends StepValue<DoubleExemplarData[]> {

        private static final DoubleExemplarData[] EMPTY = new DoubleExemplarData[0];

        private DoubleExemplarData[] current;

        private final CellSelector cellSelector;

        private Exemplars(Clock clock, long stepMillis, int size) {
            this(clock, stepMillis, new DoubleExemplarData[size], new RandomDecayingProbabilityCellSelector());
        }

        private Exemplars(Clock clock, long stepMillis, double[] buckets) {
            this(clock, stepMillis, new DoubleExemplarData[buckets.length], new HistogramCellSelector(buckets));
        }

        private Exemplars(Clock clock, long stepMillis, DoubleExemplarData[] initValue, CellSelector cellSelector) {
            super(clock, stepMillis, EMPTY);
            this.current = initValue;
            this.cellSelector = cellSelector;
        }

        @Override
        protected Supplier<DoubleExemplarData[]> valueSupplier() {
            return this::getExemplarsAndReset;
        }

        private DoubleExemplarData[] getExemplarsAndReset() {
            DoubleExemplarData[] result = current;
            current = new DoubleExemplarData[current.length];
            cellSelector.reset();
            return result;
        }

        @Override
        protected DoubleExemplarData[] noValue() {
            return EMPTY;
        }

        /**
         * Rolls the values regardless of the clock or current time and ensures the value
         * will never roll over again after.
         */
        void close() {
            this._closingRollover();
        }

        private List<DoubleExemplarData> collect() {
            List<DoubleExemplarData> exemplars = new ArrayList<>(Arrays.asList(this.poll()));
            exemplars.removeAll(Collections.singletonList(null));
            return Collections.unmodifiableList(exemplars);
        }

        private void offer(double measurement, DoubleUnaryOperator converter, OtlpExemplarContext exemplarContext,
                Clock clock) {
            int index = cellSelector.getIndex(measurement);
            if (index < current.length) {
                current[index] = createExemplar(measurement, converter, exemplarContext, clock);
            }
        }

        private static DoubleExemplarData createExemplar(double measurement, DoubleUnaryOperator converter,
                OtlpExemplarContext exemplarContext, Clock clock) {
            String traceId = exemplarContext.getTraceId();
            String spanId = exemplarContext.getSpanId();

            AttributesBuilder builder = Attributes.builder();
            for (io.micrometer.common.KeyValue keyValue : exemplarContext.getKeyValues()) {
                builder.put(keyValue.getKey(), keyValue.getValue());
            }

            SpanContext spanContext = (traceId != null && spanId != null)
                    ? SpanContext.create(traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault())
                    : SpanContext.getInvalid();

            return ImmutableDoubleExemplarData.create(builder.build(), TimeUnit.MILLISECONDS.toNanos(clock.wallTime()),
                    spanContext, converter.applyAsDouble(measurement));
        }

    }

    private static class RandomDecayingProbabilityCellSelector implements CellSelector {

        private final LongAdder count = new LongAdder();

        @Override
        public int getIndex(double ignored) {
            count.increment();
            return (int) (Math.random() * count.sum());
        }

        @Override
        public void reset() {
            count.reset();
        }

    }

    private static class HistogramCellSelector implements CellSelector {

        private final double[] buckets;

        private HistogramCellSelector(double[] buckets) {
            this.buckets = buckets;
        }

        @Override
        public int getIndex(double measurement) {
            return leastLessThanOrEqualTo(measurement);
        }

        @Override
        public void reset() {
            // no need, it's immutable
        }

        /**
         * The least bucket that is less than or equal to a sample.
         */
        private int leastLessThanOrEqualTo(double key) {
            int low = 0;
            int high = buckets.length - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (buckets[mid] < key)
                    low = mid + 1;
                else if (buckets[mid] > key)
                    high = mid - 1;
                else
                    return mid; // exact match
            }

            return low < buckets.length ? low : -1;
        }

    }

    private interface CellSelector {

        int getIndex(double measurement);

        void reset();

    }

}
