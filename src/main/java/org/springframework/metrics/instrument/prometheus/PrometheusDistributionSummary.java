package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Summary;
import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.DistributionSummary;

public class PrometheusDistributionSummary implements DistributionSummary {
    private Summary.Child summary;

    public PrometheusDistributionSummary(Summary.Child summary, Clock clock) {
        this.summary = summary;
    }

    @Override
    public void record(long amount) {
        if (amount >= 0)
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
