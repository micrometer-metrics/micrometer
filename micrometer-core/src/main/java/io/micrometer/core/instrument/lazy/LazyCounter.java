/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.lazy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;

import java.util.function.Supplier;

public final class LazyCounter implements Counter {
    private final Supplier<Counter> counterBuilder;
    private volatile Counter counter;

    private Counter counter() {
        final Counter result = counter;
        return result == null ? (counter == null ? counter = counterBuilder.get() : counter) : result;
    }

    public LazyCounter(Supplier<Counter> counterBuilder) {
        this.counterBuilder = counterBuilder;
    }

    @Override
    public String getName() {
        return counter().getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return counter().getTags();
    }

    @Override
    public String getDescription() {
        return counter().getDescription();
    }

    @Override
    public void increment(double amount) {
        counter().increment();
    }

    @Override
    public double count() {
        return counter().count();
    }
}
