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
package io.micrometer.core.instrument.graphite;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.PickledGraphite;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {

    private final GraphiteReporter reporter;
    private final GraphiteConfig config;

    public GraphiteMeterRegistry() {
        this(System::getProperty);
    }

    public GraphiteMeterRegistry(GraphiteConfig config) {
        this(config, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public GraphiteMeterRegistry(GraphiteConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        super(nameMapper, clock);

        this.config = config;
        this.config().namingConvention(new GraphiteNamingConvention());

        final PickledGraphite pickledGraphite = new PickledGraphite(new InetSocketAddress(config.host(), config.port()));
        this.reporter = GraphiteReporter.forRegistry(getDropwizardRegistry())
                .convertRatesTo(config.rateUnits())
                .convertDurationsTo(config.durationUnits())
                .build(pickledGraphite);

        if(config.enabled())
            start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
    }
}
