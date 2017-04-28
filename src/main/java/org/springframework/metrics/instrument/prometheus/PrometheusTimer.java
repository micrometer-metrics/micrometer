package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Summary;
import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.internal.AbstractTimer;

import java.util.concurrent.TimeUnit;

public class PrometheusTimer extends AbstractTimer {
    private Summary.Child summary;

    public PrometheusTimer(Summary.Child summary, Clock clock) {
        super(clock);
        this.summary = summary;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if(amount >= 0) {
            final long nanos = TimeUnit.NANOSECONDS.convert(amount, unit);
            summary.observe(nanos);
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
