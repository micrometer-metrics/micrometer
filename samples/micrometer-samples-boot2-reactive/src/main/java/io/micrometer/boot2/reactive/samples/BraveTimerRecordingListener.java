/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.boot2.reactive.samples;

import brave.Span;
import brave.Tracer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingListener;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class BraveTimerRecordingListener implements TimerRecordingListener {
    final Tracer tracer;
    // TODO not specific to this listener but leaks are possible where onStart is called but onStop is not
    ConcurrentMap<Timer.Sample, SpanContext> contextMap = new ConcurrentHashMap<>();

    BraveTimerRecordingListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onStart(Timer.Sample sample) {
        // TODO check if onStart has already been called for this sample?
        Span span = tracer.nextSpan().start();
        contextMap.computeIfAbsent(sample, key -> new SpanContext(span, tracer.withSpanInScope(span)));
    }

    @Override
    public void onError(Timer.Sample sample, Throwable throwable) {
        contextMap.get(sample).getSpan().error(throwable);
    }

    @Override
    public void onStop(Timer.Sample sample, Timer timer, Duration duration) {
        // TODO check if onStart was called for this sample and onStop hasn't been called yet?
        SpanContext context = contextMap.get(sample);
        Span span = context.getSpan().name(timer.getId().getName());
        timer.getId().getTagsAsIterable().forEach(tag -> span.tag(tag.getKey(), tag.getValue()));
        context.getSpanInScope().close();
        span.finish();
    }

    static class SpanContext {
        private final Span span;
        private final Tracer.SpanInScope spanInScope;

        SpanContext(Span span, Tracer.SpanInScope spanInScope) {
            this.span = span;
            this.spanInScope = spanInScope;
        }

        Span getSpan() {
            return span;
        }

        Tracer.SpanInScope getSpanInScope() {
            return spanInScope;
        }
    }
}
