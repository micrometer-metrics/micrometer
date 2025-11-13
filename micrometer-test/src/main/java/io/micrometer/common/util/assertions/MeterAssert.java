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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Assertion methods for {@link Meter}s.
 * <p>
 * This class can be used for all meter types that don't have a dedicated assertion
 * implementation. For specific meter types like <em>Counters</em>, <em>Timers</em>, or
 * <em>Gauges</em>, prefer using the dedicated assertion classes: {@link CounterAssert},
 * {@link TimerAssert}, or {@link GaugeAssert}.
 * <p>
 * To create a new instance of this class, invoke {@link MeterAssert#assertThat(Meter)} or
 * use {@link io.micrometer.core.tck.MeterRegistryAssert#meter(String, Tag...)}.
 *
 * @author Emanuel Trandafir
 * @see CounterAssert
 * @see TimerAssert
 * @see GaugeAssert
 */
public class MeterAssert<METER extends Meter> extends AbstractAssert<MeterAssert<METER>, METER> {

    /**
     * Creates a new instance of {@link MeterAssert}.
     * @param actual the meter to assert on
     * @param type the class type for the assertion
     */
    protected MeterAssert(METER actual, Class<? extends MeterAssert> type) {
        super(actual, type);
    }

    /**
     * Creates a new instance of {@link MeterAssert}.
     * @param actual the meter to assert on
     * @param <M> the meter type
     * @return the created assertion object
     */
    public static <M extends Meter> MeterAssert<M> assertThat(M actual) {
        return new MeterAssert<>(actual, MeterAssert.class);
    }

    /**
     * Verifies that the meter has a measurement with the given statistic and expected
     * value.
     * <p>
     * Example: <pre><code class='java'>
     * DistributionSummary summary = DistributionSummary.builder("response.size")
     *     .register(registry);
     * summary.record(100.0);
     * summary.record(200.0);
     *
     * assertThat(summary)
     *     .hasMeasurement(Statistic.COUNT, 2.0)
     *     .hasMeasurement(Statistic.TOTAL, 300.0)
     *     .hasMeasurement(Statistic.MAX, 200.0);
     * </code></pre>
     * @param statistic the statistic type to check for
     * @param expectedValue the expected value for the statistic
     * @return this assertion object for chaining
     * @see #hasType(Meter.Type)
     */
    public MeterAssert<METER> hasMeasurement(Statistic statistic, double expectedValue) {
        Optional<Double> measurement = StreamSupport.stream(actual.measure().spliterator(), false)
            .filter(m -> m.getStatistic() == statistic)
            .findAny()
            .map(Measurement::getValue);

        Assertions.assertThat(measurement)
            .as("Meter %s should have a measurement for statistic %s with value %s", actual, statistic, expectedValue)
            .isPresent()
            .hasValue(expectedValue);
        return this;
    }

    /**
     * Verifies that the meter has the expected type.
     * <p>
     * Example: <pre><code class='java'>
     * DistributionSummary summary = DistributionSummary.builder("response.size")
     *     .register(registry);
     *
     * assertThat(summary).hasType(Meter.Type.DISTRIBUTION_SUMMARY);
     * </code></pre>
     * @param expectedType the expected meter type
     * @return this assertion object for chaining
     */
    public MeterAssert<?> hasType(Meter.Type expectedType) {
        Meter.Type actualType = actual().getId().getType();
        Assertions.assertThat(actualType).isEqualTo(expectedType);
        return this;
    }

}
