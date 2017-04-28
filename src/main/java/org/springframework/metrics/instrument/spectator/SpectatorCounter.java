package org.springframework.metrics.instrument.spectator;

import org.springframework.metrics.instrument.Counter;

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
    public void increment(double amount) {
        counter.increment((long) amount);
    }

    @Override
    public long count() {
        return counter.count();
    }
}
