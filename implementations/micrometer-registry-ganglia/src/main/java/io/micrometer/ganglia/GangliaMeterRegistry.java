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
package io.micrometer.ganglia;

import com.codahale.metrics.MetricRegistry;
import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import info.ganglia.gmetric4j.gmetric.GangliaException;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * {@link StepMeterRegistry} for Ganglia.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class GangliaMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("ganglia-metrics-publisher");

    private final Logger logger = LoggerFactory.getLogger(GangliaMeterRegistry.class);

    private final GangliaConfig config;

    private final HierarchicalNameMapper nameMapper;

    private final GMetric ganglia;

    /**
     * @param config The registry configuration.
     * @param clock The clock to use for timings.
     */
    public GangliaMeterRegistry(GangliaConfig config, Clock clock) {
        this(config, clock, HierarchicalNameMapper.DEFAULT, DEFAULT_THREAD_FACTORY);
    }

    /**
     * @param config The registry configuration.
     * @param clock The clock to use for timings.
     * @param nameMapper The name mapper to use in converting dimensional metrics to
     * hierarchical names.
     * @deprecated Use {@link #builder(GangliaConfig)} instead.
     */
    @Deprecated
    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        this(config, clock, nameMapper, DEFAULT_THREAD_FACTORY);
    }

    private GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            ThreadFactory threadFactory) {
        super(config, clock);

        // Technically, Ganglia doesn't have any constraints on metric or tag names, but
        // the encoding of Unicode can look
        // horrible in the UI. So be aware...
        this.config = config;
        this.nameMapper = nameMapper;
        config().namingConvention(NamingConvention.camelCase);

        try {
            this.ganglia = new GMetric(config.host(), config.port(), config.addressingMode(), config.ttl());
            start(threadFactory);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to configure Ganglia metrics reporting", e);
        }
    }

    /**
     * @param config The registry configuration.
     * @param clock The clock to use for timings.
     * @param nameMapper The name mapper to use in converting dimensional metrics to
     * hierarchical names.
     * @param metricRegistry Ignored as of Micrometer 1.1.0.
     * @deprecated The Ganglia registry no longer uses Dropwizard as of Micrometer 1.1.0,
     * because Dropwizard dropped support for Ganglia in its 4.0.0 release. Use
     * {@link #builder(GangliaConfig)} instead.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper,
            MetricRegistry metricRegistry) {
        this(config, clock, nameMapper);
    }

    public static Builder builder(GangliaConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            // @formatter:off
            meter.use(
                    this::announceGauge,
                    this::announceCounter,
                    this::announceTimer,
                    this::announceSummary,
                    this::announceLongTaskTimer,
                    this::announceTimeGauge,
                    this::announceFunctionCounter,
                    this::announceFunctionTimer,
                    this::announceMeter);
            // @formatter:on
        }
    }

    private void announceMeter(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            announce(meter, measurement.getValue(), measurement.getStatistic().toString().toLowerCase(Locale.ROOT));
        }
    }

    private void announceFunctionTimer(FunctionTimer functionTimer) {
        announce(functionTimer, functionTimer.count(), "count");
        announce(functionTimer, functionTimer.totalTime(getBaseTimeUnit()), "sum");
        announce(functionTimer, functionTimer.mean(getBaseTimeUnit()), "avg");
    }

    private void announceFunctionCounter(FunctionCounter functionCounter) {
        announce(functionCounter, functionCounter.count());
    }

    private void announceTimeGauge(TimeGauge timeGauge) {
        announce(timeGauge, timeGauge.value(getBaseTimeUnit()));
    }

    private void announceLongTaskTimer(LongTaskTimer longTaskTimer) {
        announce(longTaskTimer, longTaskTimer.activeTasks(), "activeTasks");
        announce(longTaskTimer, longTaskTimer.duration(getBaseTimeUnit()), "duration");
    }

    private void announceSummary(DistributionSummary summary) {
        HistogramSnapshot snapshot = summary.takeSnapshot();
        announce(summary, snapshot.count(), "count");
        announce(summary, snapshot.total(), "sum");
        announce(summary, snapshot.mean(), "avg");
        announce(summary, snapshot.max(), "max");
    }

    private void announceTimer(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        announce(timer, snapshot.count(), "count");
        announce(timer, snapshot.total(getBaseTimeUnit()), "sum");
        announce(timer, snapshot.mean(getBaseTimeUnit()), "avg");
        announce(timer, snapshot.max(getBaseTimeUnit()), "max");
    }

    private void announceCounter(Counter counter) {
        announce(counter, counter.count());
    }

    private void announceGauge(Gauge gauge) {
        announce(gauge, gauge.value());
    }

    private void announce(Meter meter, double value) {
        announce(meter, value, null);
    }

    private void announce(Meter meter, double value, @Nullable String suffix) {
        Meter.Id id = meter.getId();
        String baseUnit = id.getBaseUnit();
        try {
            ganglia.announce(getMetricName(id, suffix), DoubleFormat.decimalOrNan(value), GMetricType.DOUBLE,
                    baseUnit == null ? "" : baseUnit, GMetricSlope.BOTH, (int) config.step().getSeconds(), 0,
                    "MICROMETER");
        }
        catch (GangliaException e) {
            logger.warn("Unable to publish metric " + id.getName() + " to ganglia", e);
        }
    }

    // VisibleForTesting
    String getMetricName(Meter.Id id, @Nullable String suffix) {
        return nameMapper.toHierarchicalName(id.withName(suffix != null ? id.getName() + "." + suffix : id.getName()),
                config().namingConvention());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return config.durationUnits();
    }

    public static class Builder {

        private final GangliaConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private HierarchicalNameMapper nameMapper = HierarchicalNameMapper.DEFAULT;

        Builder(GangliaConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder nameMapper(HierarchicalNameMapper nameMapper) {
            this.nameMapper = nameMapper;
            return this;
        }

        public GangliaMeterRegistry build() {
            return new GangliaMeterRegistry(config, clock, nameMapper, threadFactory);
        }

    }

}
