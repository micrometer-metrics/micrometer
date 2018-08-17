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
package io.micrometer.azure;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes Metrics to Azure Application Insights
 * @author Dhaval Doshi
 */
public class AzureMeterRegistry extends StepMeterRegistry {

    /**
     * TelemetryClient instance used to send metrics to Azure
     */
    private final TelemetryClient client;
    private final Logger logger = LoggerFactory.getLogger(AzureMeterRegistry.class);

    public AzureMeterRegistry(AzureConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public AzureMeterRegistry(AzureConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        config().namingConvention(new AzureNamingConvention());

        // Get the current active instance of TelmetryConfiguration
        TelemetryConfiguration clientConfig = TelemetryConfiguration.getActive();

        if (StringUtils.isEmpty(clientConfig.getInstrumentationKey())) {

            // Pick Instrumentation Key From the Config if not set via instrumentationKey / Environment Variable
            clientConfig.setInstrumentationKey(config.instrumentationKey());
        }

        requireNonNull(clientConfig);
        requireNonNull(clientConfig.getInstrumentationKey());

        this.client = new TelemetryClient(clientConfig);
        //config().meterFilter(MeterFilter.maximumAllowableTags(1));
        start(threadFactory);
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
                    logger.trace("unable to publish to Application Insights");
                    client.trackException(e);
                    client.flush();
                }
            });
    }

    /**
     * Utilized to transform custom Meter to Azure Metrics Format and send to Azure Monitor Backend
     * @param meterName name of the Metric
     * @param properties dimensions of metric
     * @param meter Meter holding metric values
     */
    private void trackMeter(String meterName, Map<String, String> properties, Meter meter) {
        stream(meter.measure().spliterator(), false).
            forEach(ms -> {
                client.trackMetric(meterName, ms.getValue());
            });
    }

    /**
     * Utilized to transform longTask timer into two time series with suffix _active and _duration of Azure format
     * and send to Azure Monitor endpoint
     * @param id Id of the meter
     * @param properties dimensions of LongTaskTimer
     * @param meter meter holding the rate aggregated metric values
     */
    private void trackLongTaskTimer(Meter.Id id, Map<String, String> properties,
        LongTaskTimer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(getConventionName(id, "active"), properties);
        metricTelemetry.setValue(meter.activeTasks());
        client.trackMetric(metricTelemetry);

        metricTelemetry = createAndGetBareBoneMetricTelemetry(getConventionName(id, "duration"), properties);
        metricTelemetry.setValue(meter.duration(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
    }

    /**
     * Converts DistributionSummary type meter to Azure format and transmits to Azure Monitor backend
     * @param meterName name of the DistributionSummary meter
     * @param properties dimensions of the metric
     * @param meter Meter holding rate aggregated metric values
     */
    private void trackDistributionSummary(String meterName, Map<String, String> properties,
        DistributionSummary meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalAmount());
        metricTelemetry.setCount((int)meter.count());
        metricTelemetry.setMax(meter.max());

        // TODO: Is setting min to 0th percentile value apt workaround?
        HistogramSnapshot snapshot = meter.takeSnapshot();
        Optional<ValueAtPercentile[]> opt = Optional.ofNullable(snapshot.percentileValues());
        opt.ifPresent(u -> { if (u.length > 0) metricTelemetry.setMin(u[0].value(TimeUnit.MILLISECONDS)); });
        client.trackMetric(metricTelemetry);

    }

    /**
     * Converts Timer type meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the Timer Meter
     * @param properties Dimensions of the metric
     * @param meter Timer type Meter holding rate aggregated metric values
     */
    private void trackTimer(String meterName, Map<String, String> properties, Timer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int)meter.count());

        // TODO: Is setting min to 0th percentile value apt workaround?
        HistogramSnapshot snapshot = meter.takeSnapshot();
        Optional<ValueAtPercentile[]> opt = Optional.ofNullable(snapshot.percentileValues());
        opt.ifPresent(u -> { if (u.length > 0) metricTelemetry.setMin(u[0].value(TimeUnit.MILLISECONDS)); });
        metricTelemetry.setMax(meter.max(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
        logger.info("sent timer to azure");
    }

    /**
     * Converts FunctionTimer tye meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the Function Timer
     * @param properties Dimensions of the metric
     * @param meter FunctionTimer type meter holding rate aggregated metric values
     */
    private void trackTimer(String meterName, Map<String, String> properties, FunctionTimer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int)meter.count());
        client.trackMetric(metricTelemetry);
    }

    /**
     * Converts Counter type meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the Counter
     * @param properties Dimensions of the metric
     * @param meter Counter type meter holding the rate aggregated value
     */
    private void trackCounter(String meterName, Map<String, String> properties, Counter meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());

        //TODO : Verify this conversion
        metricTelemetry.setCount((int)Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }
    /**
     * Converts FunctionCounter type meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the FunctionCounter
     * @param properties Dimensions of the metric
     * @param meter FunctionCounter type meter holding the rate aggregated value
     */
    private void trackCounter(String meterName, Map<String, String> properties, FunctionCounter meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());

        //TODO : Verify this conversion
        metricTelemetry.setCount((int)Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }
    /**
     * Converts Gauge type meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the Gauge
     * @param properties Dimensions of the metric
     * @param meter Gauge type meter holding the rate aggregated value
     */
    private void trackGauge(String meterName, Map<String, String> properties, Gauge meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value());

        //Since the value of gauge is the value at the current instant and is send over aggregate period.
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

    /**
     * Converts TimeGauge type meter to Azure Format and transmits to Azure Monitor backend
     * @param meterName Name of the TimeGauge
     * @param properties Dimensions of the metric
     * @param meter TimeGauge type meter holding the rate aggregated value
     */
    private void trackGauge(String meterName, Map<String, String> properties, TimeGauge meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value(getBaseTimeUnit()));

        //Since the value of gauge is the value at the current instant and is send over aggregate period.
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

    /**
     * Creates a barebone MetricTelemetry object populating metric name and custom dimensions
     * @param meterName Name of the meter
     * @param properties Dimensions of the meter
     * @return A barebone MetricTelemetry type object
     */
    private MetricTelemetry createAndGetBareBoneMetricTelemetry(String meterName, Map<String, String> properties) {

        MetricTelemetry metricTelemetry = new MetricTelemetry();
        metricTelemetry.setName(meterName);
        Map<String, String> metricTelemetryProperties = metricTelemetry.getContext().getProperties();
        properties.forEach(metricTelemetryProperties::putIfAbsent);
        return metricTelemetry;
    }

    private String getConventionName(Meter.Id id, String suffix) {
        return config().namingConvention()
                .name(id.getName() + "_" + suffix, id.getType(), id.getBaseUnit());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
