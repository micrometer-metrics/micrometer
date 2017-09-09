/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.spectator.step;

import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Spectator-backed registry that step-normalizes counts and sums to a rate/second over the publishing interval
 *
 * @author Jon Schneider
 */
public abstract class StepSpectatorMeterRegistry extends SpectatorMeterRegistry {
    private long stepMillis;

    public StepSpectatorMeterRegistry(Registry registry, Clock clock, long stepMillis) {
        super(registry, clock);
        this.stepMillis = stepMillis;
    }

    @Override
    public Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        List<Measurement> rateMeasurements = StreamSupport.stream(measurements.spliterator(), false)
            .map(m -> {
                switch (m.getStatistic()) {
                    case SumOfSquares:
                    case Count:
                    case Total:
                        // these values should be rate normalized
                        return new StepMeasurement(m.getValueFunction(), m.getStatistic(), clock, stepMillis);
                    case Max:
                    default:
                        return m;
                }
            }).collect(Collectors.toList());

        return super.register(id, type, rateMeasurements);
    }
}
