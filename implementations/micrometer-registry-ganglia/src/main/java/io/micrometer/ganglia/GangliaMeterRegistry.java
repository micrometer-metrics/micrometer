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
package io.micrometer.ganglia;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ganglia.GangliaReporter;
import info.ganglia.gmetric4j.gmetric.GMetric;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GangliaMeterRegistry extends DropwizardMeterRegistry {
    private final GangliaReporter reporter;
    private final GangliaConfig config;

    public GangliaMeterRegistry(GangliaConfig config, Clock clock) {
        this(config, clock, HierarchicalNameMapper.DEFAULT);
    }

    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        this(config, clock, nameMapper, new MetricRegistry());
    }

    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper, MetricRegistry metricRegistry) {
        // Technically, Ganglia doesn't have any constraints on metric or tag names, but the encoding of Unicode can look
        // horrible in the UI. So be aware...
        super(config, metricRegistry, nameMapper, clock);
        this.config = config;

        try {
            final GMetric ganglia = new GMetric(config.host(), config.port(), config.addressingMode(), config.ttl());
            this.reporter = GangliaReporter.forRegistry(getDropwizardRegistry())
                    .convertRatesTo(config.rateUnits())
                    .convertDurationsTo(config.durationUnits())
                    .build(ganglia);

            if (config.enabled())
                start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to configure Ganglia metrics reporting", e);
        }
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
    protected Double nullGaugeValue() {
        return Double.NaN;
    }
}
