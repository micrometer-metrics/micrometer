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
package io.micrometer.core.instrument.datadog;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;

import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DatadogMeterRegistry extends SpectatorMeterRegistry {
    private final long stepMillis;

    public DatadogMeterRegistry(DatadogConfig config, Clock clock) {
        super(new DatadogRegistry(config, new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }), clock, new DatadogTagFormatter());

        this.stepMillis = config.step().toMillis();

        ((DatadogRegistry) this.getSpectatorRegistry()).start();
    }

    public DatadogMeterRegistry(DatadogConfig config) {
        this(config, Clock.SYSTEM);
    }

    @Override
    public <T> T counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        return stepCounter(name, tags, obj, f, stepMillis);
    }
}
