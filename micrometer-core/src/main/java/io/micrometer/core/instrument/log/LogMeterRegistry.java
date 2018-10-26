package io.micrometer.core.instrument.log;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.step.StepMeterRegistry;

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.Meter.Type.consume;
import static io.micrometer.core.instrument.Meter.Type.match;

public class LogMeterRegistry extends StepMeterRegistry {
    public LogMeterRegistry(LogConfig config, Clock clock) {
        super(config, clock);
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            consume(meter,
                    gauge -> {

                    })
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
