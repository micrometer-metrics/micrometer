/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.HighCardinalityTagsDetector.HighCardinalityMeterInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HighCardinalityTagsDetector}
 *
 * @author Jonatan Ivanov
 */
class HighCardinalityTagsDetectorTests {

    private TestMeterNameConsumer testMeterNameConsumer;

    private SimpleMeterRegistry registry;

    private HighCardinalityTagsDetector highCardinalityTagsDetector;

    @BeforeEach
    void setUp() {
        this.testMeterNameConsumer = new TestMeterNameConsumer();
        this.registry = new SimpleMeterRegistry();
        this.highCardinalityTagsDetector = new HighCardinalityTagsDetector(registry, 3, Duration.ofMinutes(1),
                testMeterNameConsumer);
    }

    @AfterEach
    void tearDown() {
        this.highCardinalityTagsDetector.shutdown();
    }

    @Test
    void shouldDetectTagsAboveTheThreshold() {
        for (int i = 0; i < 4; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }
        highCardinalityTagsDetector.start();

        await().atMost(Duration.ofSeconds(1)).until(() -> "test.counter".equals(testMeterNameConsumer.getName()));
    }

    @Test
    void shouldNotDetectTagsOnTheThreshold() {
        for (int i = 0; i < 3; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
    }

    @Test
    void shouldNotDetectLowCardinalityTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").tag("index", "0").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
    }

    @Test
    void shouldNotDetectNoTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
    }

    @Test
    void shouldBeManagedThroughMeterRegistry() {
        for (int i = 0; i < 4; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        registry.config()
            .withHighCardinalityTagsDetector(
                    r -> new HighCardinalityTagsDetector(r, 3, Duration.ofMinutes(1), testMeterNameConsumer));

        await().atMost(Duration.ofSeconds(1)).until(() -> "test.counter".equals(testMeterNameConsumer.getName()));
    }

    @Test
    void highCardinalityMeterInfoConsumer() {
        String meterName = "test.counter";
        int meterCount = 10;

        for (int i = 0; i < meterCount; i++) {
            Counter.builder(meterName).tag("index", String.valueOf(i)).register(registry).increment();
        }

        TestMeterInfoConsumer meterInfoConsumer = new TestMeterInfoConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> new HighCardinalityTagsDetector.Builder(registry).threshold(3)
                .highCardinalityMeterInfoConsumer(meterInfoConsumer)
                .build());

        await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(meterInfoConsumer.meterInfo).isNotNull().satisfies((meterInfo) -> {
                assertThat(meterInfo.getName()).isEqualTo(meterName);
                assertThat(meterInfo.getCount()).isEqualTo(meterCount);
            }));
    }

    @Test
    void defaultSelectionKeepsFirstCandidateRatherThanHighestCount() {
        MeterRegistry orderedRegistry = orderedRegistry();
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(orderedRegistry)
            .threshold(3)
            .build()) {
            assertThat(detector.findFirst()).contains("first");
        }
    }

    @Test
    void customSelectionReceivesOnlyCandidatesAboveThreshold() {
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(orderedRegistry())
            .threshold(3)
            .highCardinalityMeterInfoSelector(candidates -> {
                List<HighCardinalityMeterInfo> infos = candidates.collect(java.util.stream.Collectors.toList());
                assertThat(infos).extracting(HighCardinalityMeterInfo::getName).containsExactly("first", "highest");
                return infos.stream().max(Comparator.comparingLong(HighCardinalityMeterInfo::getCount));
            })
            .build()) {
            assertThat(detector.findFirstHighCardinalityMeterInfo()).hasValueSatisfying(info -> {
                assertThat(info.getName()).isEqualTo("highest");
                assertThat(info.getCount()).isEqualTo(6);
            });
            // A second call must pass a fresh stream, including through the legacy API.
            assertThat(detector.findFirst()).contains("highest");
        }
    }

    @Test
    void customSelectionCanSuppressNotification() {
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(orderedRegistry())
            .threshold(3)
            .highCardinalityMeterInfoSelector(candidates -> Optional.empty())
            .build()) {
            assertThat(detector.findFirst()).isEmpty();
        }
    }

    @Test
    void customSelectionHandlesEmptyCandidateStream() {
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(orderedRegistry())
            .threshold(6)
            .highCardinalityMeterInfoSelector(
                    candidates -> candidates.max(Comparator.comparingLong(HighCardinalityMeterInfo::getCount)))
            .build()) {
            assertThat(detector.findFirstHighCardinalityMeterInfo()).isEmpty();
        }
    }

    @Test
    void scheduledChecksUseCustomSelection() {
        TestMeterInfoConsumer consumer = new TestMeterInfoConsumer();
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(orderedRegistry())
            .threshold(3)
            .highCardinalityMeterInfoSelector(
                    candidates -> candidates.max(Comparator.comparingLong(HighCardinalityMeterInfo::getCount)))
            .highCardinalityMeterInfoConsumer(consumer)
            .build()) {
            detector.start();
            await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(consumer.meterInfo).isNotNull()
                    .satisfies(info -> assertThat(info.getName()).isEqualTo("highest")));
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullSelectorIsRejected() {
        assertThatNullPointerException()
            .isThrownBy(() -> new HighCardinalityTagsDetector.Builder(registry).highCardinalityMeterInfoSelector(null));
    }

    private MeterRegistry orderedRegistry() {
        List<Meter> meters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            meters.add(registry.counter("at.threshold", "index", String.valueOf(i)));
        }
        for (int i = 0; i < 4; i++) {
            meters.add(registry.counter("first", "index", String.valueOf(i)));
        }
        for (int i = 0; i < 6; i++) {
            meters.add(registry.counter("highest", "index", String.valueOf(i)));
        }
        MeterRegistry orderedRegistry = mock(MeterRegistry.class);
        when(orderedRegistry.getMeters()).thenReturn(meters);
        return orderedRegistry;
    }

    private static class TestMeterNameConsumer implements Consumer<String> {

        private volatile @Nullable String name;

        @Override
        public void accept(String name) {
            this.name = name;
        }

        public @Nullable String getName() {
            return this.name;
        }

    }

    private static class TestMeterInfoConsumer implements Consumer<HighCardinalityMeterInfo> {

        private volatile @Nullable HighCardinalityMeterInfo meterInfo;

        @Override
        public void accept(HighCardinalityMeterInfo meterInfo) {
            this.meterInfo = meterInfo;
        }

    }

}
