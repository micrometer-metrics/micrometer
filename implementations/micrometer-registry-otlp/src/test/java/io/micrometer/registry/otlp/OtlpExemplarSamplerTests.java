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

    private final ExemplarTestRecorder.TestsExemplarContextProvider contextProvider = new ExemplarTestRecorder.TestsExemplarContextProvider();

    private final OtlpExemplarSamplerFactory factory = new OtlpExemplarSamplerFactory(contextProvider, clock, config);

    @Nested
    class FixedSizeSamplerTests {

        private static final int SIZE = 16;

        private final ExemplarSampler sampler = factory.create(SIZE, false);

        private final ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

        @Test
        void firstRecordingShouldBeAlwaysSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar expected = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(encodeHexString(exemplar.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
                assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0ba902b7");
                assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
                assertThat(exemplar.getTimeUnixNano()).isEqualTo(expected.getTimeUnixNano());
                assertThat(exemplar.getFilteredAttributesList()).isEmpty();
            });
        }

        @Test
        void keyValuesShouldPresentIfSet() {
            assertThat(sampler.collectExemplars()).isEmpty();
            KeyValues kv = KeyValues.of("a", "b", "c", "d");
            Exemplar expected = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", kv, 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(encodeHexString(exemplar.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
                assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0ba902b7");
                assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
                assertThat(exemplar.getTimeUnixNano()).isEqualTo(expected.getTimeUnixNano());
                List<KeyValue> filteredAttributes = exemplar.getFilteredAttributesList();
                assertThat(filteredAttributes).hasSize(2);
                assertThat(filteredAttributes.get(0).getKey()).isEqualTo("a");
                assertThat(filteredAttributes.get(0).getValue().getStringValue()).isEqualTo("b");
                assertThat(filteredAttributes.get(1).getKey()).isEqualTo("c");
                assertThat(filteredAttributes.get(1).getValue().getStringValue()).isEqualTo("d");
            });
        }

        @Test
        void traceIdAndSpanIdAreOptional() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar expected = recorder.record(null, null, KeyValues.of("a", "b", "c", "d"), 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(encodeHexString(exemplar.getTraceId())).isEmpty();
                assertThat(encodeHexString(exemplar.getSpanId())).isEmpty();
                assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
                assertThat(exemplar.getTimeUnixNano()).isEqualTo(expected.getTimeUnixNano());
                List<KeyValue> filteredAttributes = exemplar.getFilteredAttributesList();
                assertThat(filteredAttributes).hasSize(2);
                assertThat(filteredAttributes.get(0).getKey()).isEqualTo("a");
                assertThat(filteredAttributes.get(0).getValue().getStringValue()).isEqualTo("b");
                assertThat(filteredAttributes.get(1).getKey()).isEqualTo("c");
                assertThat(filteredAttributes.get(1).getValue().getStringValue()).isEqualTo("d");
            });
        }

        @Test
        void emptyContextIsValid() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar expected = recorder.record(null, null, (KeyValues) null, 3.14);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(encodeHexString(exemplar.getTraceId())).isEmpty();
                assertThat(encodeHexString(exemplar.getSpanId())).isEmpty();
                assertThat(exemplar.getAsDouble()).isEqualTo(3.14);
                assertThat(exemplar.getTimeUnixNano()).isEqualTo(expected.getTimeUnixNano());
                assertThat(exemplar.getFilteredAttributesList()).isEmpty();
            });
        }

        @Test
        void nullContextShouldNotBeSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            contextProvider.reset();
            sampler.sampleMeasurement(42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();
        }

        @RepeatedTest(10)
        void multipleRecordingsShouldBeRandomlySampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(5);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, 5);
        }

        @Test
        void samplerRespectsStepBoundaries() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar first = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 41.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(first);
            clock.add(STEP);

            for (int i = 0; i < 1_000; i++) {
                assertThat(sampler.collectExemplars()).isEmpty();
                Exemplar current = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", i);
                assertThat(sampler.collectExemplars()).isEmpty();
                clock.add(STEP);
                assertThat(sampler.collectExemplars()).singleElement().isEqualTo(current);
                clock.add(STEP);
            }
        }

        @Test
        void samplerRollsOverOnCloseBeforeFirstStep() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            sampler.close();
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar);
        }

        @Test
        void samplerRollsOverOnCloseAfterFirstStep() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar1);
            Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e0e4700", "00f067aa0ba90200", 3.14);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar1);
            sampler.close();
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar2);
        }

        @Test
        void samplerRollsOverOnCloseAfterEmptyStep() {
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
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(16);
        }

    }

    @Nested
    class ExplicitBucketSamplerTests {

        private final double[] buckets = new double[] { 10.0, 20.0, Double.POSITIVE_INFINITY };

        private final ExemplarSampler sampler = factory.create(buckets, false);

        private final ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

        @Test
        void recordingsShouldGoToTheRightBucket() {
            assertThat(sampler.collectExemplars()).isEmpty();
            Exemplar expected1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            Exemplar expected2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 11.0);
            Exemplar expected3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003", 21.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).hasSize(3).containsExactly(expected1, expected2, expected3);
        }

        @Test
        void sameBucketCanBeOverwritten() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            Exemplar expected = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 10.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(expected);
        }

        @Test
        void sparseBucketsAreOk() {
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();

            Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar1);

            Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 20.0);
            Exemplar exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003", 30.0);
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).containsExactly(exemplar2, exemplar3);
        }

    }

    private String encodeHexString(ByteString byteString) {
        return Hex.encodeHexString(byteString.toByteArray());
    }

}
