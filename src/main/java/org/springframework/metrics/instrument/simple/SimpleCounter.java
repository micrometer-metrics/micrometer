package org.springframework.metrics.instrument.simple;

import com.netflix.spectator.impl.AtomicDouble;
import org.springframework.metrics.instrument.Counter;

/**
 * @author Jon Schneider
 */
public class SimpleCounter implements Counter {

    AtomicDouble count = new AtomicDouble(0);

    @Override
    public void increment() {
        count.addAndGet(1.0);
    }

    @Override
    public void increment(double amount) {
        count.addAndGet(amount);
    }

    @Override
    public double count() {
        return count.get();
    }
}
