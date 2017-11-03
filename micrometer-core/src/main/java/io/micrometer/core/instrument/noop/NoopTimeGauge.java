package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.TimeGauge;

import java.util.concurrent.TimeUnit;

public class NoopTimeGauge extends NoopMeter implements TimeGauge {
    public NoopTimeGauge(Id id) {
        super(id);
    }

    @Override
    public TimeUnit getBaseTimeUnit() {
        return null;
    }

    @Override
    public double value() {
        return 0;
    }
}
