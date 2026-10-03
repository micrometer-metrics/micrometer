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
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link HighCardinalityTagsDetector}
 *
 * @author Jonatan Ivanov
 */
class HighCardinalityTagsDetectorTests {

    private TestFirstMeterInfoConsumer testFirstMeterInfoConsumer;

    private SimpleMeterRegistry registry;

    private HighCardinalityTagsDetector highCardinalityTagsDetector;

    @BeforeEach
    void setUp() {
        this.testFirstMeterInfoConsumer = new TestFirstMeterInfoConsumer();
        this.registry = new SimpleMeterRegistry();
        this.highCardinalityTagsDetector = HighCardinalityTagsDetector.builder(registry)
            .threshold(3)
            .delay(Duration.ofMinutes(1))
            .firstHighCardinalityMeterInfoConsumer(testFirstMeterInfoConsumer)
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
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isNotEmpty();
    }

    @Test
    void shouldNotDetectTagsOnTheThreshold() {
        for (int i = 0; i < 3; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
    }

    @Test
    void shouldNotDetectLowCardinalityTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").tag("index", "0").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
    }

    @Test
    void shouldNotDetectNoTags() {
        for (int i = 0; i < 5; i++) {
            Counter.builder("test.counter").register(registry).increment();
        }

        assertThat(highCardinalityTagsDetector.findFirst()).isEmpty();
        assertThat(highCardinalityTagsDetector.findFirstHighCardinalityMeterInfo()).isEmpty();
        assertThat(highCardinalityTagsDetector.findAllHighCardinalityMeterInfo()).isEmpty();
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
                .firstHighCardinalityMeterInfoConsumer(testFirstMeterInfoConsumer)
                .build());

        await().atMost(Duration.ofSeconds(1)).until(() -> "test.counter".equals(testFirstMeterInfoConsumer.getName()));
    }

    @Test
    void shouldNotAllowSettingBothConsumers() {
        assertThatThrownBy(() -> HighCardinalityTagsDetector.builder(registry)
            .firstHighCardinalityMeterInfoConsumer(new TestFirstMeterInfoConsumer())
            .allHighCardinalityMeterInfoConsumer(new TestAllMeterInfoConsumer())
            .build()).isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                    "Both firstHighCardinalityMeterInfoConsumer and allHighCardinalityMeterInfoConsumer cannot be set, you need to choose one.");
    }

    @Test
    void shouldAllowNotSettingConsumers() {
        try (HighCardinalityTagsDetector detector = HighCardinalityTagsDetector.builder(registry).build()) {
            detector.start();
        }
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

    @Test
    void firstHighCardinalityMeterInfoConsumer() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
        }

        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .firstHighCardinalityMeterInfoConsumer(testFirstMeterInfoConsumer)
                .build());

        await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(testFirstMeterInfoConsumer.meterInfo).isNotNull());

        assertThat(testFirstMeterInfoConsumer.getName()).isEqualTo("test.counter");
        assertThat(testFirstMeterInfoConsumer.getCount()).isEqualTo(10);
    }

    @Test
    void firstHighCardinalityMeterInfoConsumerIsNotCalledUnderTheThreshold() {
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .firstHighCardinalityMeterInfoConsumer(testFirstMeterInfoConsumer)
                .build());

        await().during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(testFirstMeterInfoConsumer.meterInfo).isNull());
    }

    @Test
    void allHighCardinalityMeterInfoConsumer() {
        for (int i = 0; i < 10; i++) {
            Counter.builder("test.counter").tag("index", String.valueOf(i)).register(registry).increment();
            if (i % 2 == 0) {
                Counter.builder("another.counter").tag("index", String.valueOf(i)).register(registry).increment();
            }
        }

        TestAllMeterInfoConsumer testAllMeterInfoConsumer = new TestAllMeterInfoConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .allHighCardinalityMeterInfoConsumer(testAllMeterInfoConsumer)
                .build());

        await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(testAllMeterInfoConsumer.meterInfo).hasSize(2));

        assertThat(testAllMeterInfoConsumer.meterInfo).satisfiesExactlyInAnyOrder(meterInfo -> {
            assertThat(meterInfo.getName()).isEqualTo("test.counter");
            assertThat(meterInfo.getCount()).isEqualTo(10);
        }, meterInfo -> {
            assertThat(meterInfo.getName()).isEqualTo("another.counter");
            assertThat(meterInfo.getCount()).isEqualTo(5);
        });
    }

    @Test
    void allHighCardinalityMeterInfoConsumerIsNotCalledUnderTheThreshold() {
        TestAllMeterInfoConsumer testAllMeterInfoConsumer = new TestAllMeterInfoConsumer();
        registry.config()
            .withHighCardinalityTagsDetector(registry -> HighCardinalityTagsDetector.builder(registry)
                .threshold(3)
                .allHighCardinalityMeterInfoConsumer(testAllMeterInfoConsumer)
                .build());

        await().during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(testAllMeterInfoConsumer.meterInfo).isEmpty());
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

    private static class TestFirstMeterInfoConsumer implements Consumer<HighCardinalityMeterInfo> {

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

    private static class TestAllMeterInfoConsumer implements Consumer<List<HighCardinalityMeterInfo>> {

        private volatile List<HighCardinalityMeterInfo> meterInfo = Collections.emptyList();

        @Override
        public void accept(List<HighCardinalityMeterInfo> meterInfo) {
            this.meterInfo = meterInfo;
        }

    }

}
