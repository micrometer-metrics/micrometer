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
            final double nanos = TimeUnit.NANOSECONDS.convert(amount, unit);

            // Prometheus prefers to receive everything in base units, i.e. seconds
            summary.observe(nanos / 10e8);
        }
    }

    @Override
    public long count() {
        return (long) summary.get().count;
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return secondsToUnit(summary.get().sum, unit);
    }
}
