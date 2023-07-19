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
package io.micrometer.jmx;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * @author Jon Schneider
 */
public class JmxMeterRegistry extends DropwizardMeterRegistry {

    private final JmxReporter reporter;

    public JmxMeterRegistry(JmxConfig config, Clock clock) {
        this(config, clock, HierarchicalNameMapper.DEFAULT);
    }

    public JmxMeterRegistry(JmxConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        this(config, clock, nameMapper, new MetricRegistry());
    }

    public JmxMeterRegistry(JmxConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            MetricRegistry metricRegistry) {
        this(config, clock, nameMapper, metricRegistry, defaultJmxReporter(config, metricRegistry));
    }

    public JmxMeterRegistry(JmxConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            MetricRegistry metricRegistry, JmxReporter jmxReporter) {
        super(config, metricRegistry, nameMapper, clock);
        this.reporter = jmxReporter;
        this.reporter.start();
    }

    private static JmxReporter defaultJmxReporter(JmxConfig config, MetricRegistry metricRegistry) {
        return JmxReporter.forRegistry(metricRegistry).inDomain(config.domain()).build();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

    @Override
    protected Double nullGaugeValue() {
        return Double.NaN;
    }

}
