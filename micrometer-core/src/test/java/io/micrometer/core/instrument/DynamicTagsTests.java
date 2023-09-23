/*
 * Copyright 2023 VMware, Inc.
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for convenience methods for dynamic tagging.
 *
 * @author Jonatan Ivanov
 */
class DynamicTagsTests {

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    void shouldCreateCountersDynamically() {
        Function<Tags, Counter> counterFactory = Counter.builder("test.counter").tag("static", "abc").with(registry);

        counterFactory.apply(Tags.of("dynamic", "1")).increment();
        counterFactory.apply(Tags.of("dynamic", "2")).increment();
        counterFactory.apply(Tags.of("dynamic", "1")).increment();

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.find("test.counter").tags("static", "abc", "dynamic", "1").counters()).hasSize(1);
        assertThat(registry.find("test.counter").tags("static", "abc", "dynamic", "2").counters()).hasSize(1);
    }

    @Test
    void shouldOverrideStaticTagsWhenCreatesCountersDynamically() {
        Function<Tags, Counter> counterFactory = Counter.builder("test.counter").tag("static", "abc").with(registry);

        counterFactory.apply(Tags.of("static", "xyz", "dynamic", "1")).increment();

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.find("test.counter").tags("static", "xyz", "dynamic", "1").counters()).hasSize(1);
    }

    @Test
    void shouldCreateTimersDynamically() {
        Function<Tags, Timer> timerFactory = Timer.builder("test.timer").tag("static", "abc").with(registry);

        timerFactory.apply(Tags.of("dynamic", "1")).record(Duration.ofMillis(100));
        timerFactory.apply(Tags.of("dynamic", "2")).record(Duration.ofMillis(200));
        timerFactory.apply(Tags.of("dynamic", "1")).record(Duration.ofMillis(100));

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.find("test.timer").tags("static", "abc", "dynamic", "1").timers()).hasSize(1);
        assertThat(registry.find("test.timer").tags("static", "abc", "dynamic", "2").timers()).hasSize(1);
    }

    @Test
    void shouldOverrideStaticTagsWhenCreatesTimersDynamically() {
        Function<Tags, Timer> timerFactory = Timer.builder("test.timer").tag("static", "abc").with(registry);

        timerFactory.apply(Tags.of("static", "xyz", "dynamic", "1")).record(Duration.ofMillis(100));

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.find("test.timer").tags("static", "xyz", "dynamic", "1").timers()).hasSize(1);
    }

    @Test
    void shouldCreateLongTaskTimersDynamically() {
        Function<Tags, LongTaskTimer> timerFactory = LongTaskTimer.builder("test.active.timer")
            .tag("static", "abc")
            .with(registry);

        timerFactory.apply(Tags.of("dynamic", "1")).start().stop();
        timerFactory.apply(Tags.of("dynamic", "2")).start().stop();
        timerFactory.apply(Tags.of("dynamic", "1")).start().stop();

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.find("test.active.timer").tags("static", "abc", "dynamic", "1").longTaskTimers())
            .hasSize(1);
        assertThat(registry.find("test.active.timer").tags("static", "abc", "dynamic", "2").longTaskTimers())
            .hasSize(1);
    }

    @Test
    void shouldOverrideStaticTagsWhenCreatesLongTaskTimersDynamically() {
        Function<Tags, LongTaskTimer> timerFactory = LongTaskTimer.builder("test.active.timer")
            .tag("static", "abc")
            .with(registry);

        timerFactory.apply(Tags.of("static", "xyz", "dynamic", "1")).start().stop();

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.find("test.active.timer").tags("static", "xyz", "dynamic", "1").longTaskTimers())
            .hasSize(1);
    }

    @Test
    void shouldCreateDistributionSummariesDynamically() {
        Function<Tags, DistributionSummary> distributionFactory = DistributionSummary.builder("test.distribution")
            .tag("static", "abc")
            .with(registry);

        distributionFactory.apply(Tags.of("dynamic", "1")).record(1);
        distributionFactory.apply(Tags.of("dynamic", "2")).record(2);
        distributionFactory.apply(Tags.of("dynamic", "1")).record(1);

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.find("test.distribution").tags("static", "abc", "dynamic", "1").summaries()).hasSize(1);
        assertThat(registry.find("test.distribution").tags("static", "abc", "dynamic", "2").summaries()).hasSize(1);
    }

    @Test
    void shouldOverrideStaticTagsWhenCreatesDistributionSummariesDynamically() {
        Function<Tags, DistributionSummary> distributionFactory = DistributionSummary.builder("test.distribution")
            .tag("static", "abc")
            .with(registry);

        distributionFactory.apply(Tags.of("static", "xyz", "dynamic", "1")).record(1);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.find("test.distribution").tags("static", "xyz", "dynamic", "1").summaries()).hasSize(1);
    }

}
