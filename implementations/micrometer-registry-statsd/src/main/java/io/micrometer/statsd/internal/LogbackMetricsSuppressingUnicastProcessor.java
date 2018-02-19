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