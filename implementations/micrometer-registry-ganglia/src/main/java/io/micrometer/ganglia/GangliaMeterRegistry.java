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
package io.micrometer.ganglia;

import com.codahale.metrics.MetricRegistry;
import info.ganglia.gmetric4j.gmetric.GMetric;
import info.ganglia.gmetric4j.gmetric.GMetricSlope;
import info.ganglia.gmetric4j.gmetric.GMetricType;
import info.ganglia.gmetric4j.gmetric.GangliaException;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.Meter.Type.consume;

/**
 * @author Jon Schneider
 */
public class GangliaMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(GangliaMeterRegistry.class);
    private final GangliaConfig config;
    private final HierarchicalNameMapper nameMapper;
    private final GMetric ganglia;

    /**
     * @param config The registry configuration.
     * @param clock  The clock to use for timings.
     */
    public GangliaMeterRegistry(GangliaConfig config, Clock clock) {
        this(config, clock, HierarchicalNameMapper.DEFAULT);
    }

    /**
     * @param config     The registry configuration.
     * @param clock      The clock to use for timings.
     * @param nameMapper The name mapper to use in converting dimensional metrics to hierarchical names.
     */
    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        super(config, clock);

        // Technically, Ganglia doesn't have any constraints on metric or tag names, but the encoding of Unicode can look
        // horrible in the UI. So be aware...
        this.config = config;
        this.nameMapper = nameMapper;
        this.config().namingConvention(NamingConvention.camelCase);

        try {
            this.ganglia = new GMetric(config.host(), config.port(), config.addressingMode(), config.ttl());
            if (config.enabled())
                start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to configure Ganglia metrics reporting", e);
        }
    }

    /**
     * @param config         The registry configuration.
     * @param clock          The clock to use for timings.
     * @param nameMapper     The name mapper to use in converting dimensional metrics to hierarchical names.
     * @param metricRegistry Ignored as of Micrometer 1.1.0.
     * @deprecated The Ganglia registry no longer uses Dropwizard as of Micrometer 1.1.0, because Dropwizard
     * dropped support for Ganglia in its 4.0.0 release.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public GangliaMeterRegistry(GangliaConfig config, Clock clock, HierarchicalNameMapper nameMapper, MetricRegistry metricRegistry) {
        this(config, clock, nameMapper);
    }

    @Override
    protected void publish() {
        for (Meter meter : this.getMeters()) {
            consume(meter,
                    this::announceGauge,
                    this::announceCounter,
                    this::announceTimer,
                    this::announceSummary,
                    this::announceLongTaskTimer,
                    this::announceTimeGauge,
                    this::announceFunctionCounter,
                    this::announceFunctionTimer,
                    this::announceMeter);
        }
    }

    private void announceMeter(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            announce(meter, measurement.getValue(), measurement.getStatistic().toString().toLowerCase());
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
            ganglia.announce(nameMapper.toHierarchicalName(id.withName(id.getName() + "." + suffix),
                    config().namingConvention()),
                    DoubleFormat.decimalOrNan(value),
                    GMetricType.DOUBLE,
                    baseUnit == null ? "" : baseUnit,
                    GMetricSlope.BOTH,
                    (int) config.step().getSeconds(),
                    0,
                    "MICROMETER");
        } catch (GangliaException e) {
            logger.warn("Unable to publish metric " + id.getName() + " to ganglia", e);
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
