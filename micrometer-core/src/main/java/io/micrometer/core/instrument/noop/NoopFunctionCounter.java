package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.FunctionCounter;

public class NoopFunctionCounter extends NoopMeter implements FunctionCounter {
    public NoopFunctionCounter(Id id) {
        super(id);
    }

    @Override
    public double count() {
        return 0;
    }
}
