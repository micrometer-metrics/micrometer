package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class AbstractTimer implements Timer {
    private Clock clock;

    protected AbstractTimer(Clock clock) {
        this.clock = clock;
    }

    @Override public <T> T record(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }
}
