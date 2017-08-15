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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.MeterId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

public class CompositeTimer extends AbstractTimer implements CompositeMeter {
    private final Quantiles quantiles;
    private final Histogram histogram;

    private final Map<MeterRegistry, Timer> timers = Collections.synchronizedMap(new LinkedHashMap<>());

    CompositeTimer(MeterId id, Quantiles quantiles, Histogram histogram, Clock clock) {
        super(id, clock);
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        synchronized (timers) {
            timers.values().forEach(ds -> ds.record(amount, unit));
        }
    }

    @Override
    public long count() {
        synchronized (timers) {
            return timers.values().stream().findFirst().orElse(NoopTimer.INSTANCE).count();
        }
    }

    @Override
    public double totalTime(TimeUnit unit) {
        synchronized (timers) {
            return timers.values().stream().findFirst().orElse(NoopTimer.INSTANCE).totalTime(unit);
        }
    }

    @Override
    public void add(MeterRegistry registry) {
        synchronized (timers) {
            timers.put(registry,
                registry.timerBuilder(id.getName()).tags(id.getTags()).quantiles(quantiles).histogram(histogram).create());
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (timers) {
            timers.remove(registry);
        }
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }

    @Override
    public List<Measurement> measure() {
        synchronized (timers) {
            return timers.values().stream().flatMap(c -> c.measure().stream()).collect(toList());
        }
    }
}
