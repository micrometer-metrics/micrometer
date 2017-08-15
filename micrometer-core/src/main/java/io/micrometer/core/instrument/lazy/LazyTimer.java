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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class LazyTimer implements Timer {
    private final Supplier<Timer> timerBuilder;
    private volatile Timer timer;

    private Timer timer() {
        final Timer result = timer;
        return result == null ? (timer == null ? timer = timerBuilder.get() : timer) : result;
    }

    public LazyTimer(Supplier<Timer> counterBuilder) {
        this.timerBuilder = counterBuilder;
    }

    @Override
    public String getName() {
        return timer().getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return timer().getTags();
    }

    @Override
    public List<Measurement> measure() {
        return timer().measure();
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        timer().record(amount, unit);
    }

    @Override
    public <T> T record(Supplier<T> f) {
        return timer().record(f);
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        return timer().recordCallable(f);
    }

    @Override
    public void record(Runnable f) {
        timer().record(f);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return timer().wrap(f);
    }

    @Override
    public long count() {
        return timer().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return timer().totalTime(unit);
    }
}