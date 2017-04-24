package org.springframework.metrics.collector.spectator;

import org.springframework.metrics.collector.Counter;

public class SpectatorCounter implements Counter {
    private com.netflix.spectator.api.Counter counter;

    public SpectatorCounter(com.netflix.spectator.api.Counter counter) {
        this.counter = counter;
    }

    @Override
    public void increment() {
        counter.increment();
    }

    @Override
    public void increment(long amount) {
        counter.increment(amount);
    }

    @Override
    public long count() {
        return counter.count();
    }
}
