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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
