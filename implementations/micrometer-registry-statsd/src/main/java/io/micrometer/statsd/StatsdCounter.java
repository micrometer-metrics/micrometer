package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.util.MeterEquivalence;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class StatsdCounter extends AbstractMeter implements Counter {
    private DoubleAdder count = new DoubleAdder();
    private final StatsdMeterRegistry.Writer writer;

    StatsdCounter(Id id, StatsdMeterRegistry.Writer writer) {
        super(id);
        this.writer = writer;
    }

    @Override
    public void increment(double amount) {
        if(amount > 0) {
            count.add(amount);
            writer.count((long) amount);
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
