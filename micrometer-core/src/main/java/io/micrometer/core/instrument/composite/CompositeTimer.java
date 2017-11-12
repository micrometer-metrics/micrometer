/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentracing.Tracer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CompositeTimer extends AbstractMeter implements Timer, CompositeMeter {
    private final Map<MeterRegistry, Timer> timers = new ConcurrentHashMap<>();
    private final Clock clock;
    private final HistogramConfig histogramConfig;
    private final Supplier<Tracer> tracer;

    CompositeTimer(Meter.Id id, Clock clock, HistogramConfig histogramConfig, Supplier<Tracer> tracer) {
        super(id);
        this.clock = clock;
        this.histogramConfig = histogramConfig;
        this.tracer = tracer;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        timers.values().forEach(ds -> ds.record(amount, unit));
    }

    @Override
    public void record(Duration duration) {
        firstTimer().record(duration);
    }

    @Override
    public <T> T record(Supplier<T> f) {
        return firstTimer().record(f);
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        return firstTimer().recordCallable(f);
    }

    @Override
    public void record(Runnable f) {
        firstTimer().record(f);
    }

    @Override
    public Runnable wrap(Runnable f) {
        return firstTimer().wrap(f);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return firstTimer().wrap(f);
    }

    @Override
    public long count() {
        return firstTimer().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return firstTimer().totalTime(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return firstTimer().max(unit);
    }

    @Override
    public double percentile(double percentile, TimeUnit unit) {
        return firstTimer().percentile(percentile, unit);
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        return firstTimer().histogramCountAtValue(valueNanos);
    }

    private Timer firstTimer() {
        return timers.values().stream().findFirst().orElse(new NoopTimer(getId(), clock, tracer));
    }

    @Override
    public void add(MeterRegistry registry) {
        long[] slaNanos = histogramConfig.getSlaBoundaries();
        Duration[] sla = new Duration[slaNanos.length];
        for(int i = 0; i < slaNanos.length; i++) {
            sla[i] = Duration.ofNanos(slaNanos[i]);
        }

        Timer.Builder builder = Timer.builder(getId().getName())
            .tags(getId().getTags())
            .description(getId().getDescription())
            .maximumExpectedValue(Duration.ofNanos(histogramConfig.getMaximumExpectedValue()))
            .minimumExpectedValue(Duration.ofNanos(histogramConfig.getMinimumExpectedValue()))
            .publishPercentiles(histogramConfig.getPercentiles())
            .publishPercentileHistogram(histogramConfig.isPercentileHistogram())
            .maximumExpectedValue(Duration.ofNanos(histogramConfig.getMaximumExpectedValue()))
            .minimumExpectedValue(Duration.ofNanos(histogramConfig.getMinimumExpectedValue()))
            .histogramBufferLength(histogramConfig.getHistogramBufferLength())
            .histogramExpiry(histogramConfig.getHistogramExpiry())
            .sla(sla);

        timers.put(registry, builder.register(registry));
    }

    @Override
    public void remove(MeterRegistry registry) {
        timers.remove(registry);
    }
}
