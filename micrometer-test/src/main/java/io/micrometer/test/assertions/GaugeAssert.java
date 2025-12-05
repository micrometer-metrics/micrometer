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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.DoubleAssert;

/**
 * Assertion methods for {@link Gauge}s.
 * <p>
 * To create a new instance of this class, invoke {@link GaugeAssert#assertThat(Gauge)} or
 * use {@link io.micrometer.core.tck.MeterRegistryAssert#gauge(String, Tag...)}.
 *
 * @author Emanuel Trandafir
 * @since 1.17.0
 */
public class GaugeAssert extends AbstractAssert<GaugeAssert, Gauge> {

    /**
     * Creates a new instance of {@link GaugeAssert}.
     * @param actual the gauge to assert on
     * @return the created assertion object
     */
    public static GaugeAssert assertThat(Gauge actual) {
        return new GaugeAssert(actual);
    }

    private GaugeAssert(Gauge actual) {
        super(actual, GaugeAssert.class);
    }

    /**
     * Verifies that the gauge's value is equal to the expected value.
     * <p>
     * Example: <pre><code class='java'>
     * Gauge gauge = Gauge.builder("my.gauge", () -&gt; 42.0).register(registry);
     *
     * assertThat(gauge).hasValue(42.0);
     * </code></pre>
     * @param expected the expected value
     * @return this assertion object for chaining
     * @see #value()
     */
    public GaugeAssert hasValue(double expected) {
        value().isEqualTo(expected);
        return this;
    }

    /**
     * Returns AssertJ's {@link DoubleAssert} for the gauge's current value.
     * <p>
     * Example: <pre><code class='java'>
     * Gauge gauge = Gauge.builder("my.gauge", () -&gt; 42.5).register(registry);
     *
     * assertThat(gauge).value()
     *     .isBetween(40.0, 50.0)
     *     .isCloseTo(42.0, within(1.0));
     * </code></pre>
     * @return a {@link DoubleAssert} for further assertions
     * @see #hasValue(double)
     */
    public DoubleAssert value() {
        return new DoubleAssert(actual.value());
    }

}
