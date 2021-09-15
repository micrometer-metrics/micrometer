/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * This is an internal class only for use within Micrometer.
 * This suppresses logback event metrics during Sink operations to avoid
 * infinite loops.
 */
public class LogbackMetricsSuppressingManySink implements Sinks.Many<String> {
    private final Sinks.Many<String> delegate;

    public LogbackMetricsSuppressingManySink(Sinks.Many<String> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Sinks.EmitResult tryEmitNext(String s) {
        LogbackMetrics.ignoreMetrics(() -> delegate.tryEmitNext(s));
        // We want to silently drop the element if not emitted for any reason
        // We do not use the returned result
        return Sinks.EmitResult.OK;
    }

    @Override
    public Sinks.EmitResult tryEmitComplete() {
        LogbackMetrics.ignoreMetrics(delegate::tryEmitComplete);
        // We do not use the returned result
        return Sinks.EmitResult.OK;
    }

    @Override
    public Sinks.EmitResult tryEmitError(Throwable error) {
        LogbackMetrics.ignoreMetrics(() -> delegate.tryEmitError(error));
        // We do not use the returned result
        return Sinks.EmitResult.OK;
    }

    @Override
    public void emitNext(String s, Sinks.EmitFailureHandler failureHandler) {
        LogbackMetrics.ignoreMetrics(() -> delegate.emitNext(s, failureHandler));
    }

    @Override
    public void emitComplete(Sinks.EmitFailureHandler failureHandler) {
        LogbackMetrics.ignoreMetrics(() -> delegate.emitComplete(failureHandler));
    }

    @Override
    public void emitError(Throwable error, Sinks.EmitFailureHandler failureHandler) {
        LogbackMetrics.ignoreMetrics(() -> delegate.emitError(error, failureHandler));
    }

    @Override
    public int currentSubscriberCount() {
        return delegate.currentSubscriberCount();
    }

    @Override
    public Flux<String> asFlux() {
        return delegate.asFlux();
    }

    @Override
    public Object scanUnsafe(Attr key) {
        return delegate.scanUnsafe(key);
    }
}
