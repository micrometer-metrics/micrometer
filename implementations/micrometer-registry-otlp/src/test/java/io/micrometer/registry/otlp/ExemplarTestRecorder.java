/*
 * Copyright 2026 VMware, Inc.
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
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.Clock;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

/**
 * Test helper to record Exemplars.
 *
 * @author Jonatan Ivanov
 */
class ExemplarTestRecorder {

    private final @Nullable ExemplarSampler sampler;

    private final Clock clock;

    private final TestsExemplarContextProvider contextProvider;

    ExemplarTestRecorder(TestsExemplarContextProvider contextProvider, Clock clock) {
        this(contextProvider, clock, null);
    }

    ExemplarTestRecorder(TestsExemplarContextProvider contextProvider, Clock clock, @Nullable ExemplarSampler sampler) {
        this.contextProvider = contextProvider;
        this.clock = clock;
        this.sampler = sampler;
    }

    void recordRandomMeasurements(int numberOfMeasurements) {
        for (int i = 0; i < numberOfMeasurements; i++) {
            record("4bf92f3577b34da6a3ce929d0e0e0000", "00f067aa0ba90000", i);
        }
    }

    void recordRandomMeasurements(int numberOfMeasurements, IntConsumer consumer) {
        for (int i = 1; i <= numberOfMeasurements; i++) {
            int index = i;
            record("4bf92f3577b34da6a3ce929d0e0e0000", "00f067aa0ba90000", null, () -> {
                consumer.accept(index);
                return index;
            });
        }
    }

    Exemplar record(String traceId, String spanId, double amount) {
        return record(traceId, spanId, (KeyValues) null, amount);
    }

    Exemplar record(@Nullable String traceId, @Nullable String spanId, @Nullable KeyValues keyValues, double value) {
        return record(traceId, spanId, keyValues, () -> {
            if (sampler != null) {
                sampler.sampleMeasurement(value);
            }
            return value;
        });
    }

    Exemplar record(String traceId, String spanId, Runnable runnable, double value) {
        return record(traceId, spanId, null, () -> {
            runnable.run();
            return value;
        });
    }

    Exemplar record(String traceId, String spanId, DoubleSupplier doubleSupplier) {
        return record(traceId, spanId, null, doubleSupplier);
    }

    private Exemplar record(@Nullable String traceId, @Nullable String spanId, @Nullable KeyValues keyValues,
            DoubleSupplier doubleSupplier) {
        contextProvider.setExemplar(traceId, spanId, keyValues);
        double value = doubleSupplier.getAsDouble();
        Exemplar exemplar = createExemplar(contextProvider.getExemplarContext(), value);
        contextProvider.reset();

        return exemplar;
    }

    private Exemplar createExemplar(@Nullable OtlpExemplarContext exemplarContext, double value) {
        if (exemplarContext == null) {
            throw new IllegalStateException("Exemplar context is null!");
        }

        String traceId = exemplarContext.getTraceId();
        String spanId = exemplarContext.getSpanId();
        Iterable<io.micrometer.common.KeyValue> keyValues = exemplarContext.getKeyValues();

        Exemplar.Builder builder = Exemplar.newBuilder()
            .setAsDouble(value)
            .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));

        if (traceId != null) {
            builder.setTraceId(ByteString.fromHex(traceId));
        }
        if (spanId != null) {
            builder.setSpanId(ByteString.fromHex(spanId));
        }
        if (keyValues != null) {
            for (io.micrometer.common.KeyValue keyValue : keyValues) {
                builder.addFilteredAttributes(KeyValue.newBuilder()
                    .setKey(keyValue.getKey())
                    .setValue(AnyValue.newBuilder().setStringValue(keyValue.getValue()).build())
                    .build());
            }
        }

        return builder.build();
    }

    static class TestsExemplarContextProvider implements ExemplarContextProvider {

        private @Nullable OtlpExemplarContext context;

        @Override
        public @Nullable OtlpExemplarContext getExemplarContext() {
            return context;
        }

        void setExemplar(@Nullable String traceId, @Nullable String spanId, @Nullable KeyValues keyValues) {
            context = new OtlpExemplarContext(traceId, spanId, keyValues);
        }

        void reset() {
            context = null;
        }

    }

}
