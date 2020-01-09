/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * @deprecated statsd publisher queue metrics are no longer available
 */
@Deprecated
public class StatsdMetrics implements MeterBinder {
    @Override
    public void bindTo(MeterRegistry registry) {
        if (registry instanceof StatsdMeterRegistry) {
            StatsdMeterRegistry statsdRegistry = (StatsdMeterRegistry) registry;

            Gauge.builder("statsd.queue.size", statsdRegistry, StatsdMeterRegistry::queueSize)
                    .description("The total number of StatsD events queued for transmission over UDP")
                    .register(statsdRegistry);

            Gauge.builder("statsd.queue.capacity", statsdRegistry, StatsdMeterRegistry::queueCapacity)
                    .description("The maximum number of StatsD events that can be queued for transmission")
                    .register(statsdRegistry);
        }
    }
}
