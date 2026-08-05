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

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
        public int exemplarsSize() {
            return 4;
        }

        @Override
        public @Nullable String get(@NonNull String key) {
            return null;
        }
    };

    private final MockClock clock = new MockClock();

    private final ExemplarTestRecorder.TestExemplarContextProvider contextProvider = new ExemplarTestRecorder.TestExemplarContextProvider();

    private final OtlpExemplarSamplerFactory factory = new OtlpExemplarSamplerFactory(contextProvider, clock, config);

    @Nested
    class FixedSizedCounterSamplerTests {

        private final ExemplarSampler sampler = factory.create(false);

        private final ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

        @Test
        void firstRecordingShouldBeAlwaysSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData expected = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
                assertThat(exemplar.getSpanContext().getSpanId()).isEqualTo("00f067aa0ba902b7");
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getEpochNanos()).isEqualTo(expected.getEpochNanos());
                assertThat(exemplar.getFilteredAttributes().isEmpty()).isTrue();
            });
        }

        @Test
        void keyValuesShouldPresentIfSet() {
            assertThat(sampler.collectExemplars()).isEmpty();
            KeyValues kv = KeyValues.of("a", "b", "c", "d");
            DoubleExemplarData expected = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", kv,
                    42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
                assertThat(exemplar.getSpanContext().getSpanId()).isEqualTo("00f067aa0ba902b7");
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getEpochNanos()).isEqualTo(expected.getEpochNanos());
                Attributes filteredAttributes = exemplar.getFilteredAttributes();
                assertThat(filteredAttributes.size()).isEqualTo(2);
                assertThat(filteredAttributes.get(AttributeKey.stringKey("a"))).isEqualTo("b");
                assertThat(filteredAttributes.get(AttributeKey.stringKey("c"))).isEqualTo("d");
            });
        }

        @Test
        void traceIdAndSpanIdAreOptional() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData expected = recorder.record(null, null, KeyValues.of("a", "b", "c", "d"), 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().isValid()).isFalse();
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getEpochNanos()).isEqualTo(expected.getEpochNanos());
                Attributes filteredAttributes = exemplar.getFilteredAttributes();
                assertThat(filteredAttributes.size()).isEqualTo(2);
                assertThat(filteredAttributes.get(AttributeKey.stringKey("a"))).isEqualTo("b");
                assertThat(filteredAttributes.get(AttributeKey.stringKey("c"))).isEqualTo("d");
            });
        }

        @Test
        void emptyContextIsValid() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData expected = recorder.record(null, null, KeyValues.empty(), 3.14);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().isValid()).isFalse();
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(3.14);
                assertThat(exemplar.getEpochNanos()).isEqualTo(expected.getEpochNanos());
                assertThat(exemplar.getFilteredAttributes().isEmpty()).isTrue();
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

        @Test
        void nonHexEncodedTraceIdShouldNotThrowException() {
            assertThat(sampler.collectExemplars()).isEmpty();
            contextProvider.setExemplar("abcxyz", "00f067aa0ba902b7", KeyValues.empty());
            sampler.sampleMeasurement(42.0);

            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().isValid()).isFalse();
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getFilteredAttributes().isEmpty()).isTrue();
            });
        }

        @Test
        void nonHexEncodedSpanIdShouldNotThrowException() {
            assertThat(sampler.collectExemplars()).isEmpty();
            contextProvider.setExemplar("4bf92f3577b34da6a3ce929d0e0e4736", "abcxyz", KeyValues.empty());
            sampler.sampleMeasurement(42.0);

            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().isValid()).isFalse();
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getFilteredAttributes().isEmpty()).isTrue();
            });
        }

        @RepeatedTest(10)
        void multipleRecordingsShouldBeRandomlySampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(config.exemplarsSize());
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, config.exemplarsSize());
        }

        @Test
        void samplerRespectsStepBoundaries() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData first = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 41.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(first);
            clock.add(STEP);

            for (int i = 0; i < 1_000; i++) {
                assertThat(sampler.collectExemplars()).isEmpty();
                DoubleExemplarData current = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", i);
                assertThat(sampler.collectExemplars()).isEmpty();
                clock.add(STEP);
                assertThat(sampler.collectExemplars()).singleElement().isEqualTo(current);
                clock.add(STEP);
            }
        }

        @Test
        void samplerRollsOverOnCloseBeforeFirstStep() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            sampler.close();
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar);
        }

        @Test
        void samplerRollsOverOnCloseAfterFirstStep() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7",
                    42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar1);
            DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e0e4700", "00f067aa0ba90200",
                    3.14);
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
            // likely to fill 4 exemplars
            recorder.recordRandomMeasurements(10_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(config.exemplarsSize());
        }

        @RepeatedTest(10)
        void samplerUsesSizeFromConfig() {
            OtlpConfig config = new OtlpConfig() {
                @Override
                public @NonNull Duration step() {
                    return STEP;
                }

                @Override
                public int exemplarsSize() {
                    return 4;
                }

                @Override
                public @Nullable String get(@NonNull String key) {
                    return null;
                }
            };
            ExemplarSampler sampler = new OtlpExemplarSamplerFactory(contextProvider, clock, config).create(false);
            ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(10_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(4);
        }

        @RepeatedTest(10)
        void samplerUsesDefaultSizeFromConfig() {
            OtlpConfig config = new OtlpConfig() {
                @Override
                public @NonNull Duration step() {
                    return STEP;
                }

                @Override
                public @Nullable String get(@NonNull String key) {
                    return null;
                }
            };
            ExemplarSampler sampler = new OtlpExemplarSamplerFactory(contextProvider, clock, config).create(false);
            ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(10_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(1);
        }

    }

    @Nested
    class FixedSizedExponentialHistogramSamplerTests {

        private final ExemplarSampler sampler = factory.create(config.maxBucketCount(), false);

        private final ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

        @Test
        void firstRecordingShouldBeAlwaysSampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            DoubleExemplarData expected = recorder.record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).singleElement().satisfies(exemplar -> {
                assertThat(exemplar.getSpanContext().getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
                assertThat(exemplar.getSpanContext().getSpanId()).isEqualTo("00f067aa0ba902b7");
                assertThat(((DoubleExemplarData) exemplar).getValue()).isEqualTo(42.0);
                assertThat(exemplar.getEpochNanos()).isEqualTo(expected.getEpochNanos());
                assertThat(exemplar.getFilteredAttributes().isEmpty()).isTrue();
            });
        }

        @RepeatedTest(10)
        void multipleRecordingsShouldBeRandomlySampled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(config.exemplarsSize());
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, config.exemplarsSize());
        }

        @RepeatedTest(10)
        void samplerCanBeFilled() {
            assertThat(sampler.collectExemplars()).isEmpty();
            // likely to fill 40 exemplars
            recorder.recordRandomMeasurements(1_000_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(40);
        }

        @RepeatedTest(10)
        void samplerUsesSizeFromConfig() {
            OtlpConfig config = new OtlpConfig() {
                @Override
                public @NonNull Duration step() {
                    return STEP;
                }

                @Override
                public int maxBucketCount() {
                    return 16; // -> 16/4 = 4 exemplars
                }

                @Override
                public @Nullable String get(@NonNull String key) {
                    return null;
                }
            };
            ExemplarSampler sampler = new OtlpExemplarSamplerFactory(contextProvider, clock, config)
                .create(config.maxBucketCount(), false);
            ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(10_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(4);
        }

        @RepeatedTest(10)
        void samplerUsesMinimumExemplarsSizeFromConfig() {
            OtlpConfig config = new OtlpConfig() {
                @Override
                public @NonNull Duration step() {
                    return STEP;
                }

                @Override
                public int exemplarsSize() {
                    return 2;
                }

                @Override
                public int maxBucketCount() {
                    return 4; // -> 4/4 = 1 exemplar
                }

                @Override
                public @Nullable String get(@NonNull String key) {
                    return null;
                }
            };
            ExemplarSampler sampler = new OtlpExemplarSamplerFactory(contextProvider, clock, config)
                .create(config.maxBucketCount(), false);
            ExemplarTestRecorder recorder = new ExemplarTestRecorder(contextProvider, clock, sampler);

            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.recordRandomMeasurements(10_000);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).hasSize(2);
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
            DoubleExemplarData expected1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            DoubleExemplarData expected2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                    11.0);
            DoubleExemplarData expected3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                    21.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);

            assertThat(sampler.collectExemplars()).hasSize(3).containsExactly(expected1, expected2, expected3);
        }

        @Test
        void sameBucketCanBeOverwritten() {
            assertThat(sampler.collectExemplars()).isEmpty();
            recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            DoubleExemplarData expected = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", 10.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(expected);
        }

        @Test
        void sparseBucketsAreOk() {
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();

            DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", 1.0);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).singleElement().isEqualTo(exemplar1);

            DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                    20.0);
            DoubleExemplarData exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                    30.0);
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).containsExactly(exemplar2, exemplar3);
        }

    }

}
