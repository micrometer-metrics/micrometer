/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.test.assertions;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.tck.MeterRegistryAssert;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimerAssertTest {

    SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();

    MeterRegistryAssert meterRegistryAssert = MeterRegistryAssert.assertThat(simpleMeterRegistry);

    @Test
    void shouldFindTimerByName() {
        Timer.builder("foo").register(simpleMeterRegistry).record(ofSeconds(1));

        meterRegistryAssert.timer("foo").hasCount(1).totalTime().isEqualTo(ofSeconds(1));
    }

    @Test
    void shouldThrowIfTimerNotFound() {
        Timer.builder("foo").register(simpleMeterRegistry).record(ofSeconds(1));

        assertThatThrownBy(() -> meterRegistryAssert.timer("foo", Tag.of("other-tag", "xxx")))
            .isInstanceOf(AssertionError.class)
            .hasStackTraceContaining("Meter with name <foo> and tags <[tag(other-tag=xxx)]>")
            .hasMessageContaining("Expecting actual not to be null");
    }

    @Test
    void shouldFindTimerByNameAndTags() {
        Timer.builder("foo").tag("tag-1", "aa").register(simpleMeterRegistry).record(ofSeconds(1));

        Timer.builder("foo").tag("tag-1", "bb").tag("tag-2", "cc").register(simpleMeterRegistry).record(ofSeconds(5));

        meterRegistryAssert.timer("foo", Tag.of("tag-1", "aa")).hasCount(1).totalTime().isEqualTo(ofSeconds(1));

        meterRegistryAssert.timer("foo", Tag.of("tag-1", "bb"), Tag.of("tag-2", "cc"))
            .hasCount(1)
            .totalTime()
            .isEqualTo(ofSeconds(5));
    }

    @Test
    void shouldLeverageDurationAsserts() {
        Timer.builder("foo").register(simpleMeterRegistry).record(ofSeconds(10));

        meterRegistryAssert.timer("foo")
            .totalTime()
            .isCloseTo(ofSeconds(10), ofMillis(100))
            .isBetween(ofSeconds(9), ofSeconds(11));
    }

    @Test
    void shouldAssertOnMax() {
        Timer timer = Timer.builder("foo").register(simpleMeterRegistry);

        timer.record(ofSeconds(1));
        timer.record(ofSeconds(2));
        timer.record(ofSeconds(3));

        meterRegistryAssert.timer("foo").max().isEqualTo(ofSeconds(3));
    }

    @Test
    void shouldAssertOnMean() {
        Timer timer = Timer.builder("foo").register(simpleMeterRegistry);

        timer.record(ofSeconds(1));
        timer.record(ofSeconds(2));
        timer.record(ofSeconds(3));

        meterRegistryAssert.timer("foo").mean().isEqualTo(ofSeconds(2));
    }

    @Test
    void shouldAssertOnTotalTime() {
        Timer timer = Timer.builder("foo").register(simpleMeterRegistry);

        timer.record(ofSeconds(1));
        timer.record(ofSeconds(2));
        timer.record(ofSeconds(3));

        meterRegistryAssert.timer("foo").totalTime().isEqualTo(ofSeconds(6));
    }

    @Test
    void shouldAssertOnMin() {
        Timer timer = Timer.builder("foo").register(simpleMeterRegistry);

        timer.record(ofSeconds(1));
        timer.record(ofSeconds(2));
        timer.record(ofSeconds(3));

        meterRegistryAssert.timer("foo").hasCount(3);
    }

}
