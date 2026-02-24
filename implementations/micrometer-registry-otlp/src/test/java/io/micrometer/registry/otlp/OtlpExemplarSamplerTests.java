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
import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import org.apache.commons.codec.binary.Hex;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OtlpExemplarSampler}.
 *
 * @author Jonatan Ivanov
 */
class OtlpExemplarSamplerTests {

    private static final Duration STEP = Duration.ofSeconds(1);

    private final OtlpConfig config = new OtlpConfig() {
        @Override
        public @NonNull Duration step() {
            return STEP;
        }

        @Override
        public @Nullable String get(@NonNull String key) {
            return null;
        }
    };

    private final MockClock clock = new MockClock();

    private final TestsExemplarContextProvider contextProvider = new TestsExemplarContextProvider();

    private final OtlpExemplarSamplerFactory factory = new OtlpExemplarSamplerFactory(contextProvider, clock, config);

    @Nested
    class FixedSizeSamplerTests {

        private static final int SIZE = 16;

        private final ExemplarSampler sampler = factory.create(SIZE, false);

        private final Recorder recorder = new Recorder(sampler, contextProvider);

        @Test
        void firstRecordingShouldBeAlwaysSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);

            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0ba902b7");
            assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
            assertThat(exemplar.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar.getFilteredAttributesList()).isEmpty();
        }

        @Test
        void keyValuesShouldPresentIfSet() {
            assertThat(sampler.collectExemplars()).isEmpty();
            KeyValues keyValues = KeyValues.of("a", "b", "c", "d");
            recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", keyValues, 42.0);
            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);

            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0ba902b7");
            assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
            assertThat(exemplar.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            List<KeyValue> filteredAttributes = exemplar.getFilteredAttributesList();
            assertThat(filteredAttributes).hasSize(2);
            assertThat(filteredAttributes.get(0).getKey()).isEqualTo("a");
            assertThat(filteredAttributes.get(0).getValue().getStringValue()).isEqualTo("b");
            assertThat(filteredAttributes.get(1).getKey()).isEqualTo("c");
            assertThat(filteredAttributes.get(1).getValue().getStringValue()).isEqualTo("d");
        }

        @Test
        void traceIdAndSpanIdAreOptional() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record(null, null, KeyValues.of("a", "b", "c", "d"), 42.0);
            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);

            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getTraceId())).isEmpty();
            assertThat(encodeHexString(exemplar.getSpanId())).isEmpty();
            assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
            assertThat(exemplar.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            List<KeyValue> filteredAttributes = exemplar.getFilteredAttributesList();
            assertThat(filteredAttributes).hasSize(2);
            assertThat(filteredAttributes.get(0).getKey()).isEqualTo("a");
            assertThat(filteredAttributes.get(0).getValue().getStringValue()).isEqualTo("b");
            assertThat(filteredAttributes.get(1).getKey()).isEqualTo("c");
            assertThat(filteredAttributes.get(1).getValue().getStringValue()).isEqualTo("d");
        }

        @Test
        void emptyContextIsValid() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record(null, null, null, 3.14);
            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);

            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getTraceId())).isEmpty();
            assertThat(encodeHexString(exemplar.getSpanId())).isEmpty();
            assertThat(exemplar.getAsDouble()).isEqualTo(3.14);
            assertThat(exemplar.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar.getFilteredAttributesList()).isEmpty();
        }

        @Test
        void nullContextShouldNotBeSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            contextProvider.reset();
            sampler.sampleMeasurement(42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
        }

        @RepeatedTest(10)
        void multipleRecordingsShouldBeRandomlySampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(5);
            assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, 5);
        }

        @Test
        void samplerRespectsStepBoundaries() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).hasSize(1);
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(1);
            clock.add(STEP);

            for (int i = 0; i < 1_000; i++) {
                assertThat(sampler.collectExemplars()).isEmpty();
                recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
                assertThat(sampler.collectExemplars()).isEmpty();
                clock.add(STEP);
                assertThat(sampler.collectExemplars()).hasSize(1);
                clock.add(STEP);
            }
        }

        @Test
        void samplerRollsOverOnClose() {
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            sampler.close();
            assertThat(sampler.collectExemplars()).hasSize(1);
        }

        @RepeatedTest(10)
        void samplerCanBeFilled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(1_000_000);
            assertThat(sampler.collectExemplars()).hasSize(16);
        }

    }

    @Nested
    class ExplicitBucketSamplerTests {

        private final double[] buckets = new double[] { 10.0, 20.0, Double.POSITIVE_INFINITY };

        private final ExemplarSampler sampler = factory.create(buckets, false);

        private final Recorder recorder = new Recorder(sampler, contextProvider);

        @Test
        void recordingsShouldGoToTheRightBucket() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 11.0);
            recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003", 21.0);
            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(3);

            Exemplar exemplar1 = exemplars.get(0);
            assertThat(encodeHexString(exemplar1.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e000001");
            assertThat(encodeHexString(exemplar1.getSpanId())).isEqualTo("00f067aa0b000001");
            assertThat(exemplar1.getAsDouble()).isEqualTo(1.0);
            assertThat(exemplar1.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar1.getFilteredAttributesList()).isEmpty();

            Exemplar exemplar2 = exemplars.get(1);
            assertThat(encodeHexString(exemplar2.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e000002");
            assertThat(encodeHexString(exemplar2.getSpanId())).isEqualTo("00f067aa0b000002");
            assertThat(exemplar2.getAsDouble()).isEqualTo(11.0);
            assertThat(exemplar2.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar2.getFilteredAttributesList()).isEmpty();

            Exemplar exemplar3 = exemplars.get(2);
            assertThat(encodeHexString(exemplar3.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e000003");
            assertThat(encodeHexString(exemplar3.getSpanId())).isEqualTo("00f067aa0b000003");
            assertThat(exemplar3.getAsDouble()).isEqualTo(21.0);
            assertThat(exemplar3.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar3.getFilteredAttributesList()).isEmpty();
        }

        @Test
        void sameBucketCanBeOverwritten() {
            assertThat(sampler.collectExemplars()).isEmpty();

            recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);

            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);
            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0b000001");
            assertThat(exemplar.getAsDouble()).isEqualTo(1.0);

            recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 10.0);

            exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);
            Exemplar exemplar2 = exemplars.get(0);
            assertThat(encodeHexString(exemplar2.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e000002");
            assertThat(encodeHexString(exemplar2.getSpanId())).isEqualTo("00f067aa0b000002");
            assertThat(exemplar2.getAsDouble()).isEqualTo(10.0);
            assertThat(exemplar2.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));
            assertThat(exemplar2.getFilteredAttributesList()).isEmpty();
        }

        @Test
        void sparseBucketsAreOk() {
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();

            recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            clock.add(STEP);

            List<Exemplar> exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);
            Exemplar exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0b000001");
            assertThat(exemplar.getAsDouble()).isEqualTo(1.0);

            recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 11.0);
            clock.add(STEP);

            exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);
            exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0b000002");
            assertThat(exemplar.getAsDouble()).isEqualTo(11.0);

            recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003", 21.0);
            clock.add(STEP);

            exemplars = sampler.collectExemplars();
            assertThat(exemplars).hasSize(1);
            exemplar = exemplars.get(0);
            assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0b000003");
            assertThat(exemplar.getAsDouble()).isEqualTo(21.0);
        }

    }

    private String encodeHexString(ByteString byteString) {
        return Hex.encodeHexString(byteString.toByteArray());
    }

    private static class Recorder {

        private final ExemplarSampler sampler;

        private final TestsExemplarContextProvider contextProvider;

        private Recorder(ExemplarSampler sampler, TestsExemplarContextProvider contextProvider) {
            this.sampler = sampler;
            this.contextProvider = contextProvider;
        }

        private void recordRandomMeasurements(int numberOfMeasurements) {
            for (int i = 0; i < numberOfMeasurements; i++) {
                record("4bf92f3577b34da6a3ce929d0e0e0000", "00f067aa0ba90000", i);
            }
        }

        private void record(String traceId, String spanId, double amount) {
            record(traceId, spanId, null, amount);
        }

        private void record(@Nullable String traceId, @Nullable String spanId, @Nullable KeyValues keyValues,
                double amount) {
            contextProvider.setExemplar(traceId, spanId, keyValues);
            sampler.sampleMeasurement(amount);
            contextProvider.reset();
        }

    }

    private static class TestsExemplarContextProvider implements ExemplarContextProvider {

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
