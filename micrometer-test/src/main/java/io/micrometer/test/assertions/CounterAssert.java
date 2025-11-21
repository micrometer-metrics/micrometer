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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.IntegerAssert;

/**
 * Assertion methods for {@link Counter}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link CounterAssert#assertThat(Counter)} or use
 * {@link io.micrometer.core.tck.MeterRegistryAssert#counter(String, Tag...)}.
 *
 * @author Emanuel Trandafir
 * @since 1.17.0
 */
public class CounterAssert extends AbstractAssert<CounterAssert, Counter> {

    /**
     * Creates a new instance of {@link CounterAssert}.
     * @param actual the counter to assert on
     * @return the created assertion object
     */
    public static CounterAssert assertThat(Counter actual) {
        return new CounterAssert(actual);
    }

    private CounterAssert(Counter actual) {
        super(actual, CounterAssert.class);
    }

    /**
     * Verifies that the counter's count is equal to the expected count.
     * <p>
     * Example: <pre><code class='java'>
     * Counter counter = Counter.builder("my.counter").register(registry);
     * counter.increment();
     * counter.increment();
     *
     * assertThat(counter).hasCount(2);
     * </code></pre>
     * @param expected the expected count
     * @return this assertion object for chaining
     * @see #count()
     */
    public CounterAssert hasCount(int expected) {
        count().isEqualTo(expected);
        return this;
    }

    /**
     * Returns AssertJ's {@link IntegerAssert} for the counter's current count.
     * <p>
     * Example: <pre><code class='java'>
     * Counter counter = Counter.builder("my.counter").register(registry);
     * counter.increment(5);
     *
     * assertThat(counter).count()
     *     .isBetween(1, 10)
     *     .isGreaterThan(3);
     * </code></pre>
     * @return an {@link IntegerAssert} for further assertions
     * @see #hasCount(int)
     */
    public IntegerAssert count() {
        return new IntegerAssert((int) actual.count());
    }

}
