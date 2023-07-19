/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.*;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardClock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.util.concurrent.TimeUnit;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {

    private final GraphiteConfig config;

    private final GraphiteReporter reporter;

    public GraphiteMeterRegistry(GraphiteConfig config, Clock clock) {
        this(config, clock, config.graphiteTagsEnabled() ? new GraphiteDimensionalNameMapper()
                : new GraphiteHierarchicalNameMapper(config.tagsAsPrefix()));
    }

    public GraphiteMeterRegistry(GraphiteConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        this(config, clock, nameMapper, new MetricRegistry());
    }

    public GraphiteMeterRegistry(GraphiteConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            MetricRegistry metricRegistry) {
        this(config, clock, nameMapper, metricRegistry, defaultGraphiteReporter(config, clock, metricRegistry));
    }

    public GraphiteMeterRegistry(GraphiteConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            MetricRegistry metricRegistry, GraphiteReporter reporter) {
        super(config, metricRegistry, nameMapper, clock);

        this.config = config;
        config().namingConvention(config.graphiteTagsEnabled() ? new GraphiteDimensionalNamingConvention()
                : new GraphiteHierarchicalNamingConvention());
        this.reporter = reporter;

        start();
    }

    private static GraphiteReporter defaultGraphiteReporter(GraphiteConfig config, Clock clock,
            MetricRegistry metricRegistry) {
        return GraphiteReporter.forRegistry(metricRegistry)
            .withClock(new DropwizardClock(clock))
            .convertRatesTo(config.rateUnits())
            .convertDurationsTo(config.durationUnits())
            .addMetricAttributesAsTags(config.graphiteTagsEnabled())
            .build(getGraphiteSender(config));
    }

    private static GraphiteSender getGraphiteSender(GraphiteConfig config) {
        switch (config.protocol()) {
            case PLAINTEXT:
                return new Graphite(config.host(), config.port());
            case UDP:
                return new GraphiteUDP(config.host(), config.port());
            case PICKLED:
            default:
                return new PickledGraphite(config.host(), config.port());
        }
    }

    public void stop() {
        if (config.enabled()) {
            reporter.stop();
        }
    }

    public void start() {
        if (config.enabled()) {
            reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        if (config.enabled()) {
            reporter.close();
        }
        super.close();
    }

    @Override
    @Nullable
    protected Double nullGaugeValue() {
        return null;
    }

}
