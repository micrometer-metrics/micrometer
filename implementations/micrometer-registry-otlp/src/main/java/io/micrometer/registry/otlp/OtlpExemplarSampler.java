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

import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepValue;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;

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
    public List<Exemplar> collectExemplars() {
        return exemplars.collect();
    }

    @Override
    public void close() {
        exemplars.close();
    }

    private static class Exemplars extends StepValue<Exemplar[]> {

        private static final Exemplar[] EMPTY = new Exemplar[0];

        private Exemplar[] current;

        private final CellSelector cellSelector;

        private Exemplars(Clock clock, long stepMillis, int size) {
            this(clock, stepMillis, new Exemplar[size], new RandomDecayingProbabilityCellSelector());
        }

        private Exemplars(Clock clock, long stepMillis, double[] buckets) {
            this(clock, stepMillis, new Exemplar[buckets.length], new HistogramCellSelector(buckets));
        }

        private Exemplars(Clock clock, long stepMillis, Exemplar[] initValue, CellSelector cellSelector) {
            super(clock, stepMillis, EMPTY);
            this.current = initValue;
            this.cellSelector = cellSelector;
        }

        @Override
        protected Supplier<Exemplar[]> valueSupplier() {
            return this::getExemplarsAndReset;
        }

        private Exemplar[] getExemplarsAndReset() {
            Exemplar[] result = current;
            current = new Exemplar[current.length];
            cellSelector.reset();
            return result;
        }

        @Override
        protected Exemplar[] noValue() {
            return EMPTY;
        }

        /**
         * Rolls the values regardless of the clock or current time and ensures the value
         * will never roll over again after.
         */
        void close() {
            this._closingRollover();
        }

        private List<Exemplar> collect() {
            List<Exemplar> exemplars = new ArrayList<>(Arrays.asList(this.poll()));
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

        private static Exemplar createExemplar(double measurement, DoubleUnaryOperator converter,
                OtlpExemplarContext exemplarContext, Clock clock) {
            String traceId = exemplarContext.getTraceId();
            String spanId = exemplarContext.getSpanId();

            Exemplar.Builder builder = Exemplar.newBuilder()
                .setAsDouble(converter.applyAsDouble(measurement))
                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));

            if (traceId != null) {
                builder.setTraceId(ByteString.fromHex(traceId));
                // .addFilteredAttributes(KeyValue.newBuilder()
                // .setKey("originalTraceId")
                // .setValue(AnyValue.newBuilder().setStringValue(traceId))
                // .build());
            }
            if (spanId != null) {
                builder.setSpanId(ByteString.fromHex(spanId));
                // .addFilteredAttributes(KeyValue.newBuilder()
                // .setKey("originalSpanId")
                // .setValue(AnyValue.newBuilder().setStringValue(spanId))
                // .build());
            }
            for (io.micrometer.common.KeyValue keyValue : exemplarContext.getKeyValues()) {
                builder.addFilteredAttributes(toOtelKeyValue(keyValue));
            }

            return builder.build();
        }

        private static KeyValue toOtelKeyValue(io.micrometer.common.KeyValue micrometerKeyValue) {
            return KeyValue.newBuilder()
                .setKey(micrometerKeyValue.getKey())
                .setValue(AnyValue.newBuilder().setStringValue(micrometerKeyValue.getValue()).build())
                .build();
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
