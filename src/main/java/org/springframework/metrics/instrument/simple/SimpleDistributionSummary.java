package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.DistributionSummary;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleDistributionSummary implements DistributionSummary {
    private final String name;
    private AtomicLong count = new AtomicLong(0);
    private double amount = 0.0;

    public SimpleDistributionSummary(String name) {
        this.name = name;
    }

    @Override
    public void record(double amount) {
        count.incrementAndGet();
        amount += amount;
    }

    @Override
    public long count() {
        return count.get();
    }

    @Override
    public double totalAmount() {
        return amount;
    }

    @Override
    public String getName() {
        return name;
    }
}
