package org.springframework.metrics.prometheus;

import org.springframework.metrics.Counter;

public class PrometheusCounter implements Counter {
    private io.prometheus.client.Counter.Child counter;

    public PrometheusCounter(io.prometheus.client.Counter.Child counter) {
        this.counter = counter;
    }

    @Override
    public void increment() {
        counter.inc();
    }

    @Override
    public void increment(long amount) {
        counter.inc(amount);
    }

    @Override
    public long count() {
        return (long) counter.get();
    }
}
