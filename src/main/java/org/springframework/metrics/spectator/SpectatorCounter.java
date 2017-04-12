package org.springframework.metrics.spectator;

import org.springframework.metrics.Counter;

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
