package org.springframework.metrics.collector.prometheus;

import io.prometheus.client.Summary;
import org.springframework.metrics.collector.Clock;
import org.springframework.metrics.collector.DistributionSummary;
import org.springframework.metrics.collector.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class PrometheusDistributionSummary implements DistributionSummary {
    private Summary.Child summary;

    public PrometheusDistributionSummary(Summary.Child summary, Clock clock) {
        this.summary = summary;
    }

    @Override
    public void record(long amount) {
        if(amount >= 0)
            summary.observe(amount);
    }

    @Override
    public long count() {
        return (long) summary.get().count;
    }

    @Override
    public long totalAmount() {
        return (long) summary.get().sum;
    }
}
