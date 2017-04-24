package org.springframework.metrics.collector.prometheus;

import org.springframework.metrics.collector.Counter;

public class PrometheusCounter implements Counter {
    private io.prometheus.client.Gauge.Child counter;

    public PrometheusCounter(io.prometheus.client.Gauge.Child counter) {
        this.counter = counter;
    }

    @Override
    public void increment() {
        counter.inc();
    }

    @Override
    public void increment(long amount) {
        if(amount < 0)
            counter.dec(-amount);
        else
           counter.inc(amount);
    }

    @Override
    public long count() {
        return (long) counter.get();
    }
}
