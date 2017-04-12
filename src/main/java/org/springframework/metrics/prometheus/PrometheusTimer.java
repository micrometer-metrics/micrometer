package org.springframework.metrics.prometheus;

import io.prometheus.client.Summary;
import org.springframework.metrics.Clock;
import org.springframework.metrics.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class PrometheusTimer implements Timer {
    private Summary.Child summary;
    private Clock clock;

    public PrometheusTimer(Summary.Child summary, Clock clock) {
        this.summary = summary;
        this.clock = clock;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        summary.observe(amount);
    }

    @Override
    public <T> T record(Callable<T> f) throws Exception {
        long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public long count() {
        return (long) summary.get().count;
    }

    @Override
    public long totalTime() {
        return (long) summary.get().sum;
    }
}
