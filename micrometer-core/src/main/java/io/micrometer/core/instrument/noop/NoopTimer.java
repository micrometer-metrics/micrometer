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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class NoopTimer extends NoopMeter implements Timer {
    private final Supplier<Tracer> tracer;

    public NoopTimer(Id id, Supplier<Tracer> tracer) {
        super(id);
        this.tracer = tracer;
    }

    private ActiveSpan createSpan() {
        Tracer.SpanBuilder spanBuilder = tracer.get().buildSpan(getId().getName());
        getId().getTags().forEach(t -> spanBuilder.withTag(t.getKey(), t.getValue()));
        return spanBuilder.startActive();
    }

    @Override
    public void record(long amount, TimeUnit unit) {
    }

    @Override
    public <T> T record(Supplier<T> f) {
        ActiveSpan span = createSpan();
        try {
            return f.get();
        } finally {
            span.deactivate();
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        ActiveSpan span = createSpan();
        try {
            return f.call();
        } finally {
            span.deactivate();
        }
    }

    @Override
    public void record(Runnable f) {
        ActiveSpan span = createSpan();
        try {
            f.run();
        } finally {
            span.deactivate();
        }
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return () -> {
            ActiveSpan span = createSpan();
            try {
                return f.call();
            } finally {
                span.deactivate();
            }
        };
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
    public double percentile(double percentile, TimeUnit unit) {
        return 0;
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        return 0;
    }
}
