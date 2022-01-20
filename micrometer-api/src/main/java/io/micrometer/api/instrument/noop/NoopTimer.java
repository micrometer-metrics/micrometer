/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.api.instrument.noop;

import io.micrometer.api.instrument.Timer;
import io.micrometer.api.instrument.distribution.HistogramSnapshot;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class NoopTimer extends NoopMeter implements Timer {
    public NoopTimer(Id id) {
        super(id);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
    }

    @Override
    public <T> T record(Supplier<T> f) {
        return f.get();
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        return f.call();
    }

    @Override
    public void record(Runnable f) {
        f.run();
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return 0;
    }

    @Override
    public double max(TimeUnit unit) {
        return 0;
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return HistogramSnapshot.empty(0, 0, 0);
    }
}
