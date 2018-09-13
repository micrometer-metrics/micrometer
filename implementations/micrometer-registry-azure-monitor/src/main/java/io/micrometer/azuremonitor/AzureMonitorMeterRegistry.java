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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * Publishes Metrics to Azure Monitor.
 *
 * @author Dhaval Doshi
 */
public class AzureMonitorMeterRegistry extends StepMeterRegistry {
    private static final String SDKTELEMETRY_SYNTHETIC_SOURCENAME = "SDKTelemetry";
    private static final String SDK_VERSION = "micrometer";

    private final TelemetryClient client;

    private final Logger logger = LoggerFactory.getLogger(AzureMonitorMeterRegistry.class);

    public AzureMonitorMeterRegistry(AzureMonitorConfig config, Clock clock, @Nullable TelemetryConfiguration clientConfig) {
        super(config, clock);

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
        getMeters().forEach(meter -> {
            try {
                Meter.Id id = meter.getId();
                String name = getConventionName(id);
                Map<String, String> properties = getConventionTags(id).stream()
                        .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

                if (meter instanceof TimeGauge) {
                    trackGauge(name, properties, (TimeGauge) meter);
                } else if (meter instanceof Gauge) {
                    trackGauge(name, properties, ((Gauge) meter));
                } else if (meter instanceof Counter) {
                    trackCounter(name, properties, (Counter) meter);
                } else if (meter instanceof FunctionCounter) {
                    trackCounter(name, properties, ((FunctionCounter) meter));
                } else if (meter instanceof Timer) {
                    trackTimer(name, properties, ((Timer) meter));
                } else if (meter instanceof FunctionTimer) {
                    trackTimer(name, properties, ((FunctionTimer) meter));
                } else if (meter instanceof DistributionSummary) {
                    trackDistributionSummary(name, properties, ((DistributionSummary) meter));
                } else if (meter instanceof LongTaskTimer) {
                    trackLongTaskTimer(id, properties, ((LongTaskTimer) meter));
                } else {
                    trackMeter(name, properties, meter);
                }
            } catch (Exception e) {
                logger.warn("Failed to track metric with name {}", getConventionName(meter.getId()));
                TraceTelemetry traceTelemetry = new TraceTelemetry("Failed to track metric with name " + getConventionName(meter.getId()));
                traceTelemetry.getContext().getOperation().setSyntheticSource(SDKTELEMETRY_SYNTHETIC_SOURCENAME);
                traceTelemetry.setSeverityLevel(SeverityLevel.Warning);
                client.trackTrace(traceTelemetry);
                client.flush();
            }
        });
    }

    private void trackMeter(String meterName, Map<String, String> properties, Meter meter) {
        stream(meter.measure().spliterator(), false)
                .forEach(ms -> {
                    MetricTelemetry mt = createMetricTelemetry(meterName, properties);
                    mt.setValue(ms.getValue());
                    client.track(mt);
                });
    }

    /**
     * Utilized to transform longTask timer into two time series with suffix _active and _duration of Azure format
     * and send to Azure Monitor endpoint
     *
     * @param id         Id of the meter
     * @param properties dimensions of LongTaskTimer
     * @param meter      meter holding the rate aggregated metric values
     */
    private void trackLongTaskTimer(Meter.Id id, Map<String, String> properties,
                                    LongTaskTimer meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(getConventionName(id, "active"), properties);
        metricTelemetry.setValue(meter.activeTasks());
        client.trackMetric(metricTelemetry);

        metricTelemetry = createMetricTelemetry(getConventionName(id, "duration"), properties);
        metricTelemetry.setValue(meter.duration(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
    }

    private void trackDistributionSummary(String meterName, Map<String, String> properties,
                                          DistributionSummary meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalAmount());
        metricTelemetry.setCount((int) meter.count());
        metricTelemetry.setMax(meter.max());
        metricTelemetry.setMin(0.0); // TODO: when #457 is resolved, support min
        client.trackMetric(metricTelemetry);
    }

    private void trackTimer(String meterName, Map<String, String> properties, Timer meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int) meter.count());
        metricTelemetry.setMin(0.0); // TODO: when #457 is resolved, support min
        metricTelemetry.setMax(meter.max(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
    }

    private void trackTimer(String meterName, Map<String, String> properties, FunctionTimer meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int) meter.count());
        client.trackMetric(metricTelemetry);
    }

    private void trackCounter(String meterName, Map<String, String> properties, Counter meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());
        metricTelemetry.setCount((int) Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }

    private void trackCounter(String meterName, Map<String, String> properties, FunctionCounter meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());
        metricTelemetry.setCount((int) Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }

    private void trackGauge(String meterName, Map<String, String> properties, Gauge meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value());
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

    private void trackGauge(String meterName, Map<String, String> properties, TimeGauge meter) {
        MetricTelemetry metricTelemetry = createMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value(getBaseTimeUnit()));
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

    private MetricTelemetry createMetricTelemetry(String meterName, Map<String, String> properties) {
        MetricTelemetry metricTelemetry = new MetricTelemetry();
        metricTelemetry.setName(meterName);
        Map<String, String> metricTelemetryProperties = metricTelemetry.getContext().getProperties();
        properties.forEach(metricTelemetryProperties::putIfAbsent);
        return metricTelemetry;
    }

    private String getConventionName(Meter.Id id, String suffix) {
        return config().namingConvention()
                .name(id.getName() + "." + suffix, id.getType(), id.getBaseUnit());
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
