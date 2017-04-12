package org.springframework.metrics.spectator;

import org.springframework.metrics.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SpectatorTimer implements Timer {
    private com.netflix.spectator.api.Timer timer;

    public SpectatorTimer(com.netflix.spectator.api.Timer timer) {
        this.timer = timer;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        timer.record(amount, unit);
    }

    @Override
    public <T> T record(Callable<T> f) throws Exception {
        return timer.record(f);
    }

    @Override
    public void record(Runnable f) {
        timer.record(f);
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
