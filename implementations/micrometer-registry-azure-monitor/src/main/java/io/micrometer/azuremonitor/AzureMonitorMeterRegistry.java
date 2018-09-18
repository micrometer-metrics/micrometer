/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.azuremonitor;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.Meter.Type.match;
import static java.util.stream.StreamSupport.stream;

/**
 * Publishes Metrics to Azure Monitor.
 *
 * @author Dhaval Doshi
 * @author Jon Schneider
 */
public class AzureMonitorMeterRegistry extends StepMeterRegistry {
    private static final String SDKTELEMETRY_SYNTHETIC_SOURCENAME = "SDKTelemetry";
    private static final String SDK_VERSION = "micrometer";

    private final Logger logger = LoggerFactory.getLogger(AzureMonitorMeterRegistry.class);
    private final TelemetryClient client;
    private final AzureMonitorConfig config;

    public AzureMonitorMeterRegistry(AzureMonitorConfig config, Clock clock, @Nullable TelemetryConfiguration clientConfig) {
        super(config, clock);
        this.config = config;

        config().namingConvention(new AzureMonitorNamingConvention());

        if (clientConfig == null) {
            clientConfig = TelemetryConfiguration.getActive();
        }

        if (StringUtils.isEmpty(clientConfig.getInstrumentationKey())) {
            clientConfig.setInstrumentationKey(config.instrumentationKey());
        }

        this.client = new TelemetryClient(clientConfig);
        client.getContext().getInternal().setSdkVersion(SDK_VERSION);

        start();
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            match(meter,
                    this::trackGauge,
                    this::trackCounter,
                    this::trackTimer,
                    this::trackDistributionSummary,
                    this::trackLongTaskTimer,
                    this::trackTimeGauge,
                    this::trackFunctionCounter,
                    this::trackFunctionTimer,
                    this::trackMeter
            ).forEach(telemetry -> {
                try {
                    client.track(telemetry);
                } catch (Throwable e) {
                    logger.warn("Failed to track metric {}", meter.getId());
                    TraceTelemetry traceTelemetry = new TraceTelemetry("Failed to track metric " + meter.getId());
                    traceTelemetry.getContext().getOperation().setSyntheticSource(SDKTELEMETRY_SYNTHETIC_SOURCENAME);
                    traceTelemetry.setSeverityLevel(SeverityLevel.Warning);
                    client.trackTrace(traceTelemetry);
                    client.flush();
                }
            });
        }
    }

    private Stream<MetricTelemetry> trackMeter(Meter meter) {
        return stream(meter.measure().spliterator(), false)
                .map(ms -> {
                    MetricTelemetry mt = createMetricTelemetry(meter, ms.getStatistic().toString().toLowerCase());
                    mt.setValue(ms.getValue());
                    return mt;
                });
    }

    private Stream<MetricTelemetry> trackLongTaskTimer(LongTaskTimer timer) {
        MetricTelemetry active = createMetricTelemetry(timer, "active");
        active.setValue(timer.activeTasks());

        MetricTelemetry duration = createMetricTelemetry(timer, "duration");
        duration.setValue(timer.duration(getBaseTimeUnit()));

        return Stream.of(active, duration);
    }

    private Stream<MetricTelemetry> trackDistributionSummary(DistributionSummary summary) {
        MetricTelemetry mt = createMetricTelemetry(summary, null);
        mt.setValue(summary.totalAmount());
        mt.setCount((int) summary.count());
        mt.setMax(summary.max());
        mt.setMin(0.0); // TODO: when #457 is resolved, support min
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackTimer(Timer timer) {
        MetricTelemetry mt = createMetricTelemetry(timer, null);
        mt.setValue(timer.totalTime(getBaseTimeUnit()));
        mt.setCount((int) timer.count());
        mt.setMin(0.0); // TODO: when #457 is resolved, support min
        mt.setMax(timer.max(getBaseTimeUnit()));
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackFunctionTimer(FunctionTimer timer) {
        MetricTelemetry mt = createMetricTelemetry(timer, null);
        mt.setValue(timer.totalTime(getBaseTimeUnit()));
        mt.setCount((int) timer.count());
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackCounter(Counter counter) {
        MetricTelemetry mt = createMetricTelemetry(counter, null);
        mt.setValue(counter.count());
        mt.setCount((int) Math.round(counter.count()));
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackFunctionCounter(FunctionCounter counter) {
        MetricTelemetry mt = createMetricTelemetry(counter, null);
        mt.setValue(counter.count());
        mt.setCount((int) Math.round(counter.count()));
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackGauge(Gauge gauge) {
        MetricTelemetry mt = createMetricTelemetry(gauge, null);
        mt.setValue(gauge.value());
        mt.setCount(1);
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackTimeGauge(TimeGauge meter) {
        MetricTelemetry mt = createMetricTelemetry(meter, null);
        mt.setValue(meter.value(getBaseTimeUnit()));
        mt.setCount(1);
        return Stream.of(mt);
    }

    private MetricTelemetry createMetricTelemetry(Meter meter, @Nullable String suffix) {
        MetricTelemetry mt = new MetricTelemetry();

        Meter.Id id = meter.getId();
        mt.setName(config().namingConvention().name(id.getName() + (suffix == null ? "" : "." + suffix),
                id.getType(), id.getBaseUnit()));

        for (Tag tag : getConventionTags(meter.getId())) {
            mt.getContext().getProperties().putIfAbsent(tag.getKey(), tag.getValue());
        }

        return mt;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() {
        client.flush();
        super.close();
    }
}
