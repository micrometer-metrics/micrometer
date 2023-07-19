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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.function.LongConsumer;

/**
 * This is an internal class only for use within Micrometer. This suppresses logback event
 * metrics during Sink operations to avoid infinite loops.
 */
public class LogbackMetricsSuppressingFluxSink implements FluxSink<String> {

    private final FluxSink<String> delegate;

    public LogbackMetricsSuppressingFluxSink(FluxSink<String> delegate) {
        this.delegate = delegate;
    }

    @Override
    public FluxSink<String> next(String s) {
        LogbackMetrics.ignoreMetrics(() -> delegate.next(s));
        return this;
    }

    @Override
    public void complete() {
        LogbackMetrics.ignoreMetrics(delegate::complete);
    }

    @Override
    public void error(Throwable e) {
        LogbackMetrics.ignoreMetrics(() -> delegate.error(e));
    }

    @Deprecated
    @Override
    public Context currentContext() {
        return delegate.currentContext();
    }

    @Override
    public ContextView contextView() {
        return delegate.contextView();
    }

    @Override
    public long requestedFromDownstream() {
        return delegate.requestedFromDownstream();
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public FluxSink<String> onRequest(LongConsumer consumer) {
        LogbackMetrics.ignoreMetrics(() -> delegate.onRequest(consumer));
        return this;
    }

    @Override
    public FluxSink<String> onCancel(Disposable d) {
        LogbackMetrics.ignoreMetrics(() -> delegate.onCancel(d));
        return this;
    }

    @Override
    public FluxSink<String> onDispose(Disposable d) {
        LogbackMetrics.ignoreMetrics(() -> delegate.onDispose(d));
        return this;
    }

}
