package io.micrometer.core.instrument.datadog;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;

/**
 * @author Jon Schneider
 */
public class DatadogMeterRegistry extends SpectatorMeterRegistry {
    public DatadogMeterRegistry(Clock clock, DatadogConfig config) {
        super(new DatadogRegistry(new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return System.currentTimeMillis();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }, config));

        ((DatadogRegistry) this.getSpectatorRegistry()).start();
    }

    public DatadogMeterRegistry(DatadogConfig config) {
        this(Clock.SYSTEM, config);
    }
}
