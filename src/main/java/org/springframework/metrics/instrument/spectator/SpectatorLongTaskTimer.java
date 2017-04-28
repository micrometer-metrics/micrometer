package org.springframework.metrics.instrument.spectator;

import org.springframework.metrics.instrument.LongTaskTimer;

public class SpectatorLongTaskTimer implements LongTaskTimer {
    private final com.netflix.spectator.api.LongTaskTimer timer;

    public SpectatorLongTaskTimer(com.netflix.spectator.api.LongTaskTimer timer) {
        this.timer = timer;
    }

    @Override
    public long start() {
        return timer.start();
    }

    @Override
    public long stop(long task) {
        return timer.stop(task);
    }

    @Override
    public long duration(long task) {
        return timer.duration(task);
    }

    @Override
    public long duration() {
        return timer.duration();
    }

    @Override
    public int activeTasks() {
        return timer.activeTasks();
    }
}