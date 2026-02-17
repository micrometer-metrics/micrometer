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
import java.util.function.Supplier;

class OtlpExemplarSampler implements ExemplarSampler {

    private static final int DEFAULT_EXEMPLARS_SIZE = 16;

    private final ExemplarContextProvider exemplarContextProvider;

    private final Clock clock;

    private final Exemplars exemplars;

    OtlpExemplarSampler(ExemplarContextProvider exemplarContextProvider, Clock clock, long stepMillis) {
        this.exemplarContextProvider = exemplarContextProvider;
        this.clock = clock;
        this.exemplars = new Exemplars(clock, stepMillis, DEFAULT_EXEMPLARS_SIZE);
    }

    @Override
    public void sampleMeasurement(double measurement) {
        OtlpExemplarContext exemplarContext = exemplarContextProvider.getExemplarContext();
        if (exemplarContext != null) {
            exemplars.offer(measurement, exemplarContext, clock);
        }
    }

    @Override
    public List<Exemplar> collectExemplars() {
        return exemplars.collect();
    }

    private static class Exemplars extends StepValue<Exemplar[]> {

        private static final Exemplar[] EMPTY = new Exemplar[0];

        private Exemplar[] exemplars;

        private final LongAdder offeredExemplars;

        private Exemplars(Clock clock, long stepMillis, int size) {
            this(clock, stepMillis, new Exemplar[size]);
        }

        private Exemplars(Clock clock, long stepMillis, Exemplar[] initValue) {
            super(clock, stepMillis, initValue);
            this.exemplars = initValue;
            this.offeredExemplars = new LongAdder();
        }

        @Override
        protected Supplier<Exemplar[]> valueSupplier() {
            return this::getExemplarsAndReset;
        }

        private Exemplar[] getExemplarsAndReset() {
            Exemplar[] result = exemplars;
            exemplars = new Exemplar[exemplars.length];
            offeredExemplars.reset();
            return result;
        }

        @Override
        protected Exemplar[] noValue() {
            return EMPTY;
        }

        private List<Exemplar> collect() {
            List<Exemplar> exemplars = new ArrayList<>(Arrays.asList(this.poll()));
            exemplars.removeAll(Collections.singletonList(null));
            return Collections.unmodifiableList(exemplars);
        }

        private void offer(double measurement, OtlpExemplarContext exemplarContext, Clock clock) {
            // OTel does something similar
            offeredExemplars.increment();
            int index = (int) (Math.random() * offeredExemplars.sum());
            if (index < exemplars.length) {
                exemplars[index] = createExemplar(measurement, exemplarContext, clock);
            }
        }

        private static Exemplar createExemplar(double measurement, OtlpExemplarContext exemplarContext, Clock clock) {
            String traceId = exemplarContext.getTraceId();
            String spanId = exemplarContext.getSpanId();
            Iterable<io.micrometer.common.KeyValue> keyValues = exemplarContext.getKeyValues();

            Exemplar.Builder builder = Exemplar.newBuilder()
                .setAsDouble(measurement)
                // .addFilteredAttributes(KeyValue.newBuilder()
                // .setKey("originalTraceId")
                // .setValue(AnyValue.newBuilder().setStringValue(traceId))
                // .build())
                // .addFilteredAttributes(KeyValue.newBuilder()
                // .setKey("originalSpanId")
                // .setValue(AnyValue.newBuilder().setStringValue(spanId))
                // .build())
                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));

            if (traceId != null) {
                builder.setTraceId(ByteString.fromHex(traceId));
            }
            if (spanId != null) {
                builder.setSpanId(ByteString.fromHex(spanId));
            }
            if (keyValues != null) {
                for (io.micrometer.common.KeyValue keyValue : keyValues) {
                    builder.addFilteredAttributes(toOtelKeyValue(keyValue));
                }
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

}
