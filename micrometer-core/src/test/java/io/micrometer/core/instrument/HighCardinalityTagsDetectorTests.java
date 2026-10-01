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

import io.micrometer.core.instrument.HighCardinalityTagsDetector.HighCardinalityDetections;
import io.micrometer.core.instrument.HighCardinalityTagsDetector.HighCardinalityMeterInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link HighCardinalityTagsDetector}
 *
 * @author Jonatan Ivanov
 */
class HighCardinalityTagsDetectorTests {

    private TestMeterInfoConsumer testFirstMeterInfoConsumer;

    private SimpleMeterRegistry registry;

    private HighCardinalityTagsDetector highCardinalityTagsDetector;

    @BeforeEach
    void setUp() {
        this.testFirstMeterInfoConsumer = new TestMeterInfoConsumer();
        this.registry = new SimpleMeterRegistry();
        this.highCardinalityTagsDetector = HighCardinalityTagsDetector.builder(registry)
            .threshold(3)
            .delay(Duration.ofMinutes(1))
            .highCardinalityMeterInfoConsumer(testFirstMeterInfoConsumer)
            .build();
    }

    @AfterEach
    void tearDown() {
        this.highCardinalityTagsDetector.close();
    }

    @Test
    void shouldDetectTagsAboveTheThreshold() {
        for (int i = 0; i < 4; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }
        highCardinalityTagsDetector.start();

        await().atMost(Duration.ofSeconds(1)).until(() -> "test.counter".equals(testFirstMeterInfoConsumer.getName()));
        assertThat(highCardinalityTagsDetector.findFirst()).isNotEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isNotEmpty();
        assertThat(highCardinalityTagsDetector.findHighestHighCardinalityMeterInfo()).isNotEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isNotEmpty();
        assertThat(highCardinalityTagsDetector.findDetections()).isNotEmpty();
    }

    @Test
    void shouldNotDetectTagsOnTheThreshold() {
        for (int i = 0; i < 3; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findHighestHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findDetections()).isEmpty();
    }

    @Test
    void shouldNotDetectLowCardinalityTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").tag("index", "0").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findHighestHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findDetections()).isEmpty();
    }

    @Test
    void shouldNotDetectNoTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findHighestHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findDetections()).isEmpty();
    }

    @Test
    void shouldBeManagedThroughMeterRegistry() {
        for (int i = 0; i < 4; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .delay(Duration.ofMinutes(1))
                .detectionsConsumer(detections -> detections.findFirst().ifPresent(testFirstMeterInfoConsumer))
                .build());

        await().atMost(Duration.ofSeconds(1)).until(() -> "test.counter".equals(testFirstMeterInfoConsumer.getName()));
    }

    @Test
    void shouldAllowNotSettingConsumers() {
        try (HighCardinalityTagsDetector detector = HighCardinalityTagsDetector.builder(registry).build()) {
            detector.start();
        }
    }

    @Test
    void detectionsConsumerHandlingAllDetections() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("http.requests").tag("index", String.valueOf(i)).register(registry).increment();
            if (i % 2 == 0) {
                Counter.builder("db.calls").tag("index", String.valueOf(i)).register(registry).increment();
            }
        }

        List<HighCardinalityMeterInfo> collected = Collections.synchronizedList(new ArrayList<>());
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .detectionsConsumer(detections -> {
                    // Typical usage: iterate directly over detections (implements
                    // Iterable)
                    for (HighCardinalityMeterInfo info : detections) {
                        collected.add(info);
                    }
                })
                .build());

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(collected).hasSize(2));

        assertThat(collected).containsExactlyInAnyOrder(new HighCardinalityMeterInfo("http.requests", 10),
                new HighCardinalityMeterInfo("db.calls", 5));
    }

    @Test
    void detectionsConsumerHandlingHighestCardinalityDetection() {
        // "http.requests" has 10 meters, "db.calls" has 5 meters.
        for (int i = 0; i < 10; i++) {
            Counter.builder("http.requests").tag("index", String.valueOf(i)).register(registry).increment();
            if (i % 2 == 0) {
                Counter.builder("db.calls").tag("index", String.valueOf(i)).register(registry).increment();
            }
        }

        AtomicReference<HighCardinalityMeterInfo> highestOffender = new AtomicReference<>();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                // Typical usage for gh-7649: get the single meter with highest
                // cardinality
                .detectionsConsumer(detections -> detections.findHighest().ifPresent(highestOffender::set))
                .build());

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(highestOffender.get()).isNotNull());

        assertThat(highestOffender.get().getName()).isEqualTo("http.requests");
        assertThat(highestOffender.get().getCount()).isEqualTo(10);
    }

    @Test
    void detectionsConsumerHandlingFirstDetection() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        AtomicReference<HighCardinalityMeterInfo> firstDetected = new AtomicReference<>();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                // Typical usage: get the first detection
                .detectionsConsumer(detections -> detections.findFirst().ifPresent(firstDetected::set))
                .build());

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(firstDetected.get()).isNotNull());

        assertThat(firstDetected.get().getName()).isEqualTo("test.counter");
        assertThat(firstDetected.get().getCount()).isEqualTo(10);
    }

    @Test
    void detectionsConsumerIsNotCalledUnderTheThreshold() {
        AtomicReference<HighCardinalityDetections> received = new AtomicReference<>();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .detectionsConsumer(received::set)
                .build());

        await().during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(received.get()).isNull());
    }

    @Test
    void oneTimeChecksAndQueryMethods() {
        for (int i = 0; i < 15; i++) {
            Counter.builder("http.requests").tag("index", String.valueOf(i)).register(registry).increment();
            if (i < 8) {
                Counter.builder("db.calls").tag("index", String.valueOf(i)).register(registry).increment();
            }
        }

        try (HighCardinalityTagsDetector detector = HighCardinalityTagsDetector.builder(registry)
            .threshold(5)
            .build()) {

            HighCardinalityDetections detections = detector.findDetections();
            assertThat(detections).hasSize(2);
            assertThat(detections.isEmpty()).isFalse();

            // Stream operations on detections
            List<String> names = detections.stream().map(HighCardinalityMeterInfo::getName).toList();
            assertThat(names).containsExactlyInAnyOrder("http.requests", "db.calls");

            // Direct query methods
            assertThat(detector.findHighestHighCardinalityMeterInfo()).isNotEmpty().get().satisfies(info -> {
                assertThat(info.getName()).isEqualTo("http.requests");
                assertThat(info.getCount()).isEqualTo(15);
            });

            assertThat(detector.findFirstHighCardinalityMeterInfo()).isNotEmpty();
            assertThat(detector.findAllHighCardinalityMeterInfo()).hasSize(2);
            assertThat(detector.findFirst()).contains("http.requests");
        }
    }

    @Test
    void highCardinalityMeterInfoConsumerBackwardsCompatibility() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        TestMeterInfoConsumer consumer = new TestMeterInfoConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .highCardinalityMeterInfoConsumer(consumer)
                .build());

        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(consumer.meterInfo).isNotNull());

        assertThat(consumer.getName()).isEqualTo("test.counter");
        assertThat(consumer.getCount()).isEqualTo(10);
    }

    @Test
    void highCardinalityMeterInfoConsumerIsNotCalledUnderTheThreshold() {
        TestMeterInfoConsumer consumer = new TestMeterInfoConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .highCardinalityMeterInfoConsumer(consumer)
                .build());

        await().during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(consumer.meterInfo).isNull());
    }

    @Test
    void firstHighCardinalityMeterNameConsumer() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        TestMeterNameConsumer testMeterNameConsumer = new TestMeterNameConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> new HighCardinalityTagsDetector(registry, 3,
                    Duration.ofMinutes(1), testMeterNameConsumer));

        await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(testMeterNameConsumer.getName()).isEqualTo("test.counter"));
    }

    @Test
    void firstHighCardinalityMeterNameConsumerIsNotCalledUnderTheThreshold() {
        TestMeterNameConsumer testMeterNameConsumer = new TestMeterNameConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> new HighCardinalityTagsDetector(registry, 3,
                    Duration.ofMinutes(1), testMeterNameConsumer));

        await().during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(testMeterNameConsumer.getName()).isNull());
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

        public @Nullable String getName() {
            HighCardinalityMeterInfo meterInfo = this.meterInfo;
            return meterInfo != null ? meterInfo.getName() : null;
        }

        public @Nullable Long getCount() {
            HighCardinalityMeterInfo meterInfo = this.meterInfo;
            return meterInfo != null ? meterInfo.getCount() : null;
        }

    }

}
