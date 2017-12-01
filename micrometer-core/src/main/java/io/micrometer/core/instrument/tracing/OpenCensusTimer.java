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
package io.micrometer.core.instrument.tracing;

import io.micrometer.core.instrument.HistogramSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class OpenCensusTimer implements Timer {

    public static Builder builder(String name, Tracer tracer) {
        return new Builder(name, tracer);
    }

    public static class Builder extends Timer.Builder{
        private final Tracer tracer;

        Builder(String name, Tracer tracer) {
            super(name);
            this.tracer = tracer;
        }

        @Override
        public OpenCensusTimer register(MeterRegistry registry) {
            return new OpenCensusTimer(super.register(registry), tracer);
        }
    }

    private final Timer delegate;
    private final Tracer tracer;

    OpenCensusTimer(Timer delegate, Tracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    private Scope createSpan() {
        Scope spanBuilder = tracer.spanBuilder(getId().getName()).startScopedSpan();
        getId().getTags().forEach(t -> tracer.getCurrentSpan().putAttribute(t.getKey(), AttributeValue.stringAttributeValue(t.getValue())));
        return spanBuilder;
    }


    @Override
    public <T> T record(Supplier<T> f) {
        try(@SuppressWarnings("unused") Scope span = createSpan()){
            return delegate.record(f);
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        try(@SuppressWarnings("unused") Scope span = createSpan()) {
            return delegate.recordCallable(f);
        }
    }

    @Override
    public void record(Runnable f) {
        try(@SuppressWarnings("unused") Scope span = createSpan()) {
            delegate.record(f);
        }
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return () -> {
            try(@SuppressWarnings("unused") Scope span = createSpan()) {
                return delegate.wrap(f).call();
            }
        };
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        delegate.record(amount, unit);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return delegate.totalTime(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return delegate.max(unit);
    }

    @Override
    public double percentile(double percentile, TimeUnit unit) {
        return delegate.percentile(percentile, unit);
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        return delegate.histogramCountAtValue(valueNanos);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return delegate.takeSnapshot(supportsAggregablePercentiles);
    }

    @Override
    public Id getId() {
        return delegate.getId();
    }
}
