/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardClock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {

    private final GraphiteReporter reporter;
    private final GraphiteConfig config;

    public GraphiteMeterRegistry(GraphiteConfig config, Clock clock) {
        this(config, clock, new GraphiteHierarchicalNameMapper(config.tagsAsPrefix()));
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
        this.config().namingConvention(new GraphiteNamingConvention());
        this.reporter = reporter;

        if (config.enabled())
            start();
    }

    private static GraphiteReporter defaultGraphiteReporter(GraphiteConfig config, Clock clock, MetricRegistry metricRegistry) {
        GraphiteSender sender;
        switch (config.protocol()) {
            case PLAINTEXT:
                sender = new Graphite(new InetSocketAddress(config.host(), config.port()));
                break;
            case UDP:
                sender = new GraphiteUDP(new InetSocketAddress(config.host(), config.port()));
                break;
            case PICKLED:
            default:
                sender = new PickledGraphite(new InetSocketAddress(config.host(), config.port()));
        }

        return GraphiteReporter.forRegistry(metricRegistry)
            .withClock(new DropwizardClock(clock))
            .convertRatesTo(config.rateUnits())
            .convertDurationsTo(config.durationUnits())
            .build(sender);
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        reporter.report();
        stop();
        super.close();
    }

    @Override
    @Nullable
    protected Double nullGaugeValue() {
        return null;
    }
}
