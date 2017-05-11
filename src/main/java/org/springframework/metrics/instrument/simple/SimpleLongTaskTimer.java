package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.LongTaskTimer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleLongTaskTimer implements LongTaskTimer {
    private final String name;
    private final ConcurrentMap<Long, Long> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextTask = new AtomicLong(0L);

    private final Clock clock;

    public SimpleLongTaskTimer(String name, Clock clock) {
        this.name = name;
        this.clock = clock;
    }

    @Override
    public long start() {
        long task = nextTask.getAndIncrement();
        tasks.put(task, clock.monotonicTime());
        return task;
    }

    @Override
    public long stop(long task) {
        Long startTime = tasks.get(task);
        if (startTime != null) {
            tasks.remove(task);
            return clock.monotonicTime() - startTime;
        } else {
            return -1L;
        }
    }

    @Override
    public long duration(long task) {
        Long startTime = tasks.get(task);
        return (startTime != null) ? (clock.monotonicTime() - startTime) : -1L;
    }

    @Override
    public long duration() {
        long now = clock.monotonicTime();
        long sum = 0L;
        for (long startTime : tasks.values()) {
            sum += now - startTime;
        }
        return sum;
    }

    @Override
    public int activeTasks() {
        return tasks.size();
    }

    @Override
    public String getName() {
        return name;
    }
}
