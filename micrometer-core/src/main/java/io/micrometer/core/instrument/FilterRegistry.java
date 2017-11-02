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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.filter.FilterProperties;
import io.micrometer.core.instrument.histogram.StatsConfig;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class FilterRegistry extends MeterRegistry {

    private final MeterRegistry delegate;
    private final FilterProperties filterProperties;
    private final ConcurrentHashMap<Meter.Id, FilterResult> filtered = new ConcurrentHashMap<>();
    private final CompositeMeterRegistry noopRegistry = new CompositeMeterRegistry();
    private final More more = new FilterMore();

    public FilterRegistry(MeterRegistry delegate, FilterProperties filterProperties) {
        this(delegate, filterProperties, Clock.SYSTEM);
    }

    public FilterRegistry(MeterRegistry delegate, FilterProperties filterProperties, Clock clock) {
        super(clock);
        this.delegate = delegate;
        this.filterProperties = filterProperties;
    }

    @Override
    public Collection<Meter> getMeters() {
        return delegate.getMeters();
    }

    @Override
    public Config config() {
        return delegate.config();
    }

    @Override
    public Search find(String name) {
        return delegate.find(name);
    }

    @Override
    public More more() {
        return more;
    }

    @Override
    Counter counter(Meter.Id id) {
        FilterResult result = filter(id);
        return result.registry.counter(result.id);
    }

    @Override
    Timer timer(Meter.Id id, StatsConfig statsConfig) {
        FilterResult result = filter(id);
        return result.registry.timer(result.id, statsConfig);
    }

    @Override
    <T> Gauge gauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        FilterResult result = filter(id);
        return result.registry.gauge(result.id, obj, f);
    }

    @Override
    DistributionSummary summary(Meter.Id id, StatsConfig statsConfig) {
        FilterResult result = filter(id);
        return result.registry.summary(result.id, statsConfig);
    }

    @Override
    Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        FilterResult result = filter(id);
        return result.registry.register(result.id, type, measurements);
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        //No OP since we delegate before getting to this point
        return null;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        //No OP since we delegate before getting to this point
        return null;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        //No OP since we delegate before getting to this point
        return null;
    }

    @Override
    protected Timer newTimer(Meter.Id id, StatsConfig statsConfig) {
        //No OP since we delegate before getting to this point
        return null;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, StatsConfig statsConfig) {
        //No OP since we delegate before getting to this point
        return null;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        //No OP since we delegate before getting to this point
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return null;
    }

    public class FilterMore extends More {

        @Override
        LongTaskTimer longTaskTimer(Meter.Id id) {
            FilterResult result = filter(id);
            return result.registry.more().longTaskTimer(result.id);
        }

        @Override
        <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
            FilterResult result = filter(id);
            return result.registry.more().counter(result.id, obj, f);
        }

        @Override
        <T> FunctionTimer timer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
            FilterResult result = filter(id);
            return result.registry.more().timer(result.id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
        }

        @Override
        <T> TimeGauge timeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
            FilterResult result = filter(id);
            return result.registry.more().timeGauge(result.id, obj, fUnit, f);
        }
    }

    private FilterResult filter(Meter.Id id){
        FilterResult existingResult = filtered.get(id);
        if(existingResult != null) {
            return existingResult;
        }

        String filterStatus = filterProperties.filterStatus(id.getName());
        MeterRegistry reg = filterStatus.equals(FilterProperties.EXCLUDE) ? noopRegistry : delegate;

        Iterable<Tag> filteredTags = filterProperties.combineTags(id);
        Meter.Id resultId = new Meter.Id(id.getName(),filteredTags, id.getBaseUnit(), id.getDescription());

        FilterResult result = new FilterResult(reg, resultId);
        filtered.put(id, result);
        return result;
    }

    public static class FilterResult {
        private final MeterRegistry registry;
        private final Meter.Id id;

        public FilterResult(MeterRegistry registry, Meter.Id id) {
            this.registry = registry;
            this.id = id;
        }
    }
}
