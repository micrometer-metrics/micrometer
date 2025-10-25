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
package io.micrometer.common.util.assertions;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.DoubleAssert;
import org.assertj.core.api.DurationAssert;
import org.assertj.core.api.IntegerAssert;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

/**
 * Assertion methods for {@link Timer}s.
 * <p>
 * To create a new instance of this class, invoke {@link TimerAssert#assertThat(Timer)} or
 * use {@link io.micrometer.core.tck.MeterRegistryAssert#timer(String, Tag...)}.
 *
 * @author Emanuel Trandafir
 */
public class TimerAssert extends AbstractAssert<TimerAssert, Timer> {

    /**
     * Creates a new instance of {@link TimerAssert}.
     * @param actual the timer to assert on
     * @return the created assertion object
     */
    public static TimerAssert assertThat(Timer actual) {
        return new TimerAssert(actual);
    }

    private TimerAssert(Timer actual) {
        super(actual, TimerAssert.class);
    }

    /**
     * Returns AssertJ's {@link org.assertj.core.api.DurationAssert} for the total time
     * recorded by this timer.
     * <p>
     * Example: <pre><code class='java'>
     * Timer timer = Timer.builder("my.timer").register(registry);
     * timer.record(Duration.ofSeconds(5));
     * timer.record(Duration.ofSeconds(3));
     *
     * assertThat(timer)
     *     .totalTime()
     *     .isEqualTo(Duration.ofSeconds(8))
     *     .isBetween(Duration.ofSeconds(7), Duration.ofSeconds(9));
     * </code></pre>
     * @return a {@link DurationAssert} for further assertions
     * @see #max()
     * @see #mean()
     */
    public DurationAssert totalTime() {
        return toDurationAssert(actual::totalTime);
    }

    /**
     * Returns AssertJ's {@link DurationAssert} for the maximum recorded duration.
     * <p>
     * Example: <pre><code class='java'>
     * Timer timer = Timer.builder("my.timer").register(registry);
     * timer.record(Duration.ofSeconds(1));
     * timer.record(Duration.ofSeconds(5));
     * timer.record(Duration.ofSeconds(3));
     *
     * assertThat(timer).max()
     *     .isEqualTo(Duration.ofSeconds(5))
     *     .isGreaterThan(Duration.ofSeconds(4));
     * </code></pre>
     * @return a {@link DurationAssert} for further assertions
     * @see #totalTime()
     * @see #mean()
     */
    public DurationAssert max() {
        return toDurationAssert(actual::max);
    }

    /**
     * Returns AssertJ's {@link DurationAssert} for the mean (average) duration.
     * <p>
     * Example: <pre><code class='java'>
     * Timer timer = Timer.builder("my.timer").register(registry);
     * timer.record(Duration.ofSeconds(1));
     * timer.record(Duration.ofSeconds(2));
     * timer.record(Duration.ofSeconds(3));
     *
     * assertThat(timer).mean()
     *     .isEqualTo(Duration.ofSeconds(2));
     * </code></pre>
     * @return a {@link DurationAssert} for further assertions
     * @see #totalTime()
     * @see #max()
     */
    public DurationAssert mean() {
        return toDurationAssert(actual::mean);
    }

    /**
     * Verifies that the timer's count is equal to the expected count.
     * <p>
     * Example: <pre><code class='java'>
     * Timer timer = Timer.builder("my.timer").register(registry);
     * timer.record(Duration.ofSeconds(1));
     * timer.record(Duration.ofSeconds(2));
     *
     * assertThat(timer).hasCount(2);
     * </code></pre>
     * @param expectedCount the expected count
     * @return this assertion object for chaining
     * @see #count()
     */
    public TimerAssert hasCount(int expectedCount) {
        count().isEqualTo(expectedCount);
        return this;
    }

    /**
     * Returns AssertJ's {@link IntegerAssert} for the number of times the timer has been
     * recorded.
     * <p>
     * Example: <pre><code class='java'>
     * Timer timer = Timer.builder("my.timer").register(registry);
     * timer.record(Duration.ofSeconds(1));
     * timer.record(Duration.ofSeconds(2));
     *
     * assertThat(timer).count()
     *     .isBetween(1, 10)
     *     .isGreaterThan(1);
     * </code></pre>
     * @return an {@link IntegerAssert} for further assertions
     * @see #hasCount(int)
     */
    public IntegerAssert count() {
        return new IntegerAssert((int) actual.count());
    }

    private static DurationAssert toDurationAssert(Function<TimeUnit, Double> accessor) {
        double nanos = accessor.apply(TimeUnit.NANOSECONDS);
        return new DurationAssert(Duration.ofNanos((long) nanos));
    }

}
