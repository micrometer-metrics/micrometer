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
import io.micrometer.core.instrument.MockClock;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import org.apache.commons.codec.binary.Hex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
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

    private final MockClock clock = new MockClock();

    private final TestsExemplarContextProvider exemplarContextProvider = new TestsExemplarContextProvider();

    private final ExemplarSampler sampler = new OtlpExemplarSampler(exemplarContextProvider, clock, STEP.toMillis());

    @Test
    void firstRecordingShouldBeAlwaysSampled() {
        assertThat(sampler.collectExemplars()).isEmpty();
        record("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", 42.0);
        List<Exemplar> exemplars = sampler.collectExemplars();
        assertThat(exemplars).hasSize(1);

        Exemplar exemplar = exemplars.get(0);
        assertThat(encodeHexString(exemplar.getTraceId())).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(encodeHexString(exemplar.getSpanId())).isEqualTo("00f067aa0ba902b7");
        assertThat(exemplar.getAsDouble()).isEqualTo(42.0);
        assertThat(exemplar.getTimeUnixNano()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()));

        List<KeyValue> filteredAttributes = exemplar.getFilteredAttributesList();
        assertThat(filteredAttributes).hasSize(2);
        assertThat(filteredAttributes.get(0).getKey()).isEqualTo("originalTraceId");
        assertThat(filteredAttributes.get(0).getValue().getStringValue()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(filteredAttributes.get(1).getKey()).isEqualTo("originalSpanId");
        assertThat(filteredAttributes.get(1).getValue().getStringValue()).isEqualTo("00f067aa0ba902b7");
    }

    @RepeatedTest(10)
    void multipleRecordingsShouldBeRandomlySampled() {
        assertThat(sampler.collectExemplars()).isEmpty();
        List<Measurable> measurable = Arrays.asList(
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0001", "00f067aa0ba90001", 41.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0002", "00f067aa0ba90002", 42.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0003", "00f067aa0ba90003", 43.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0004", "00f067aa0ba90004", 44.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0005", "00f067aa0ba90005", 45.0));
        record(measurable);
        assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, measurable.size());
    }

    @RepeatedTest(10)
    void samplerResetsOverTime() {
        List<Measurable> measurables = Arrays.asList(
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0001", "00f067aa0ba90001", 41.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0002", "00f067aa0ba90002", 42.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0003", "00f067aa0ba90003", 43.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0004", "00f067aa0ba90004", 44.0),
                new Measurable("4bf92f3577b34da6a3ce929d0e0e0005", "00f067aa0ba90005", 45.0));

        for (int i = 0; i < 1_000; i++) {
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();
            record(measurables);
            assertThat(sampler.collectExemplars()).isEmpty();
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).doesNotHaveDuplicates().hasSizeBetween(1, measurables.size());
            clock.add(STEP);
            assertThat(sampler.collectExemplars()).isEmpty();
        }
    }

    @Test
    void nullContextShouldNotBeSampled() {
        assertThat(sampler.collectExemplars()).isEmpty();
        sampler.sampleMeasurement(42.0);
        assertThat(sampler.collectExemplars()).isEmpty();
    }

    @RepeatedTest(10)
    void samplerCanBeFilled() {
        assertThat(sampler.collectExemplars()).isEmpty();
        for (int i = 0; i < 1_000_000; i++) {
            record("4bf92f3577b34da6a3ce929d0e0e0000", "00f067aa0ba90000", i);
        }
        assertThat(sampler.collectExemplars()).hasSize(16);
    }

    private void record(List<Measurable> measurables) {
        for (Measurable measurable : measurables) {
            record(measurable);
        }
    }

    private void record(String traceId, String spanId, double amount) {
        record(new Measurable(traceId, spanId, amount));
    }

    private void record(Measurable measurable) {
        exemplarContextProvider.setExemplar(measurable.traceId, measurable.spanId);
        sampler.sampleMeasurement(measurable.amount);
        exemplarContextProvider.reset();
    }

    private String encodeHexString(ByteString byteString) {
        return Hex.encodeHexString(byteString.toByteArray());
    }

    private static class Measurable {

        private final String traceId;

        private final String spanId;

        private final double amount;

        Measurable(String traceId, String spanId, double amount) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.amount = amount;
        }

    }

    private static class TestsExemplarContextProvider implements ExemplarContextProvider {

        private @Nullable OtlpExemplarContext context;

        @Override
        public @Nullable OtlpExemplarContext getExemplarContext() {
            return context;
        }

        void setExemplar(String traceId, String spanId) {
            context = new OtlpExemplarContext(traceId, spanId);
        }

        void reset() {
            context = null;
        }

    }

}
