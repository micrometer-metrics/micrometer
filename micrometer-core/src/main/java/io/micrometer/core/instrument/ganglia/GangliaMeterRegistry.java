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
package io.micrometer.core.instrument.ganglia;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ganglia.GangliaReporter;
import info.ganglia.gmetric4j.gmetric.GMetric;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GangliaMeterRegistry extends DropwizardMeterRegistry {
    public static class Builder {
        private MetricRegistry registry = new MetricRegistry();
        private HierarchicalNameMapper nameMapper = new HierarchicalNameMapper();
        private Clock clock = Clock.SYSTEM;
        private GangliaConfig config = System::getProperty;

        public Builder nameMapper(HierarchicalNameMapper nameMapper) {
            this.nameMapper = nameMapper;
            return this;
        }

        public Builder config(GangliaConfig config) {
            this.config = config;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder registry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public GangliaMeterRegistry create() {
            return new GangliaMeterRegistry(registry, nameMapper, clock, config);
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public static GangliaMeterRegistry local() {
        return build().create();
    }

    private GangliaMeterRegistry(MetricRegistry registry, HierarchicalNameMapper nameMapper, Clock clock, GangliaConfig config) {
        super(registry, nameMapper, clock);

        try {
            final GMetric ganglia = new GMetric(config.host(), config.port(), config.addressingMode(), config.ttl());
            final GangliaReporter reporter = GangliaReporter.forRegistry(registry)
                    .convertRatesTo(config.rateUnits())
                    .convertDurationsTo(config.durationUnits())
                    .build(ganglia);
            reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
        } catch (IOException e) {
            throw new RuntimeException("Failed to configure Ganglia metrics reporting", e);
        }
    }
}
