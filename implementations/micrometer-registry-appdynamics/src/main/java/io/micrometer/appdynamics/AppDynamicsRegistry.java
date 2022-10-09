/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.appdynamics;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.MetricPublisher;
import io.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.appdynamics.aggregation.MetricSnapshotProvider;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link StepMeterRegistry} for AppDynamics.
 *
 * @author Ricardo Veloso
 */
public class AppDynamicsRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(AppDynamicsRegistry.class);

    private final AppDynamicsConfig config;

    private final MetricPublisher publisher;

    public AppDynamicsRegistry(AppDynamicsConfig config, Clock clock) {
        this(config, AppdynamicsAgent.getMetricPublisher(), clock);
    }

    public AppDynamicsRegistry(AppDynamicsConfig config, MetricPublisher publisher, Clock clock) {
        super(config, clock);
        this.config = config;
        this.publisher = publisher;
        config().namingConvention(new PathNamingConvention(config));
        start(new NamedThreadFactory("appd-metrics-publisher"));
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to appdynamics every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return new AppDynamicsTimer(id, clock, pauseDetector, getBaseTimeUnit(), this.config.step().toMillis());
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new AppDynamicsDistributionSummary(id, clock, scale);
    }

    @Override
    protected void publish() {
        logger.info("publishing to appdynamics");

        getMeters().forEach(meter ->
        // @formatter:off
                meter.use(
                        gauge -> publishObservation(meter.getId(), (long) gauge.value()),
                        counter -> publishSumValue(meter.getId(), (long) counter.count()),
                        timer -> publishSnapshot(meter.getId(), (MetricSnapshotProvider) timer),
                        summary -> publishSnapshot(meter.getId(), (MetricSnapshotProvider) summary),
                        this::publishLongTaskTimer,
                        timeGauge -> publishObservation(meter.getId(), (long) timeGauge.value()),
                        functionCounter -> publishSumValue(meter.getId(), (long) functionCounter.count()),
                        this::publishFunctionTimer,
                        this::noOpPublish));
        // @formatter:on
    }

    private void noOpPublish(Meter meter) {
    }

    private void publishFunctionTimer(FunctionTimer meter) {
        long total = (long) meter.totalTime(getBaseTimeUnit());
        long average = (long) meter.mean(getBaseTimeUnit());
        publishAggregation(meter.getId(), (long) meter.count(), total, average, average);
    }

    private void publishLongTaskTimer(LongTaskTimer meter) {
        long duration = (long) meter.duration(getBaseTimeUnit());
        long max = (long) meter.max(getBaseTimeUnit());
        publishAggregation(meter.getId(), meter.activeTasks(), duration, 0, max);
    }

    private void publishSnapshot(Meter.Id id, MetricSnapshotProvider provider) {
        MetricSnapshot snapshot = provider.snapshot();
        publishAggregation(id, snapshot.count(), (long) snapshot.total(), (long) snapshot.min(), (long) snapshot.max());
    }

    private void publishSumValue(Meter.Id id, long value) {
        logger.debug("publishing sum {}", id.getName());
        publisher.reportMetric(getConventionName(id), value, "SUM", "SUM", "COLLECTIVE");
    }

    private void publishObservation(Meter.Id id, long value) {
        logger.debug("publishing observation {}", id.getName());
        publisher.reportMetric(getConventionName(id), value, "OBSERVATION", "CURRENT", "COLLECTIVE");
    }

    private void publishAggregation(Meter.Id id, long count, long value, long min, long max) {
        if (count > 0) {
            logger.debug("publishing aggregation {}", id.getName());
            publisher.reportMetric(getConventionName(id), value, count, min, max, "AVERAGE", "AVERAGE", "INDIVIDUAL");
        } else {
            logger.debug("ignore aggregation with no observation {}", id.getName());
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return config.getBaseTimeUnit();
    }

}
