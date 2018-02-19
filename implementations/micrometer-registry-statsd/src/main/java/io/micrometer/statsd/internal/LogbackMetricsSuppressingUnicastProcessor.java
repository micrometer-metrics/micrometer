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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.UnicastProcessor;

public class LogbackMetricsSuppressingUnicastProcessor implements Processor<String, String> {
    private final UnicastProcessor<String> processor;

    public LogbackMetricsSuppressingUnicastProcessor(UnicastProcessor<String> processor) {
        this.processor = processor;
    }

    @Override
    public void subscribe(Subscriber<? super String> s) {
        processor.subscribe(s);
    }

    @Override
    public void onSubscribe(Subscription s) {
        processor.onSubscribe(s);
    }

    @Override
    public void onNext(String s) {
        LogbackMetrics.ignoreMetrics(() -> processor.onNext(s));
    }

    @Override
    public void onError(Throwable t) {
        LogbackMetrics.ignoreMetrics(() -> processor.onError(t));
    }

    @Override
    public void onComplete() {
        LogbackMetrics.ignoreMetrics(processor::onComplete);
    }

    public int size() {
        return processor.size();
    }

    public int getBufferSize() {
        return processor.getBufferSize();
    }
}