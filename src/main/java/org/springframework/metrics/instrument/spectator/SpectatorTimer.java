package org.springframework.metrics.instrument.spectator;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.internal.AbstractTimer;

import java.util.concurrent.TimeUnit;

public class SpectatorTimer extends AbstractTimer {
    private com.netflix.spectator.api.Timer timer;

    public SpectatorTimer(com.netflix.spectator.api.Timer timer, Clock clock) {
        super(clock);
        this.timer = timer;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        timer.record(amount, unit);
    }

    @Override
    public long count() {
        return timer.count();
    }

    @Override
    public long totalTime() {
        return timer.totalTime();
    }
}
