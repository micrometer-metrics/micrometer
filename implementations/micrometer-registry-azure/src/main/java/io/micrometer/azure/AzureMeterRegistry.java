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
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureMeterRegistry extends StepMeterRegistry {
    private final TelemetryClient client;
    private final Logger logger = LoggerFactory.getLogger(AzureMeterRegistry.class);

    public AzureMeterRegistry(AzureConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public AzureMeterRegistry(AzureConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        config().namingConvention(new AzureNamingConvention());

        TelemetryConfiguration clientConfig = TelemetryConfiguration.getActive();

        if (StringUtils.isEmpty(clientConfig.getInstrumentationKey())) {

            // Pick Instrumentation Key From the Config if not set via instrumentationKey / Environment Variable
            clientConfig.setInstrumentationKey(config.instrumentationKey());
        }

        requireNonNull(clientConfig);
        requireNonNull(clientConfig.getInstrumentationKey());

        this.client = new TelemetryClient(clientConfig);
        //config().meterFilter(MeterFilter.maximumAllowableTags(1)
        start(threadFactory);
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            Meter.Id id = meter.getId();
            String name = getConventionName(id);
            Map<String, String> properties = getConventionTags(id).stream()
                    .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

            if (meter instanceof TimeGauge) {
                trackGauge(name, properties, (TimeGauge) meter);
            } else if (meter instanceof Gauge) {
                trackGauge(name, properties, ((Gauge)meter));
            } else if (meter instanceof Counter) {
                trackCounter(name, properties, (Counter)meter);
            } else if (meter instanceof FunctionCounter) {
                trackCounter(name, properties, ((FunctionCounter)meter));
            } else if (meter instanceof Timer) {
                trackTimer(name, properties, ((Timer) meter));
            } else if (meter instanceof FunctionTimer) {
                trackTimer(name , properties, ((FunctionTimer) meter));
            } else if (meter instanceof DistributionSummary) {
                trackDistributionSummary(name, properties, ((DistributionSummary) meter));
            } else if (meter instanceof LongTaskTimer) {
                trackLongTaskTimer(id, properties, ((LongTaskTimer)meter));
            } else {
                trackMeter(name, properties, meter);
            }

        }
    }

    private void trackMeter(String meterName, Map<String, String> properties, Meter meter) {
        stream(meter.measure().spliterator(), false).
            map(ms -> {
                client.trackMetric(meterName, ms.getValue());
                return null;
            });
    }

    private void trackLongTaskTimer(Meter.Id id, Map<String, String> properties,
        LongTaskTimer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(getConventionName(id, "active"), properties);
        metricTelemetry.setValue(meter.activeTasks());
        client.trackMetric(metricTelemetry);

        metricTelemetry = createAndGetBareBoneMetricTelemetry(getConventionName(id, "duration"), properties);
        metricTelemetry.setValue(meter.duration(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
    }

    private void trackDistributionSummary(String meterName, Map<String, String> properties,
        DistributionSummary meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalAmount());
        metricTelemetry.setCount((int)meter.count());
        metricTelemetry.setMax(meter.max());

        // TODO: Is setting min to 0th percentile value apt workaround?
        metricTelemetry.setMin((meter.takeSnapshot().percentileValues())[0].value(TimeUnit.MILLISECONDS));
        client.trackMetric(metricTelemetry);

    }

    private void trackTimer(String meterName, Map<String, String> properties, Timer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int)meter.count());

        // TODO: Is setting min to 0th percentile value apt workaround?
        metricTelemetry.setMin((meter.takeSnapshot().percentileValues())[0].value(TimeUnit.MILLISECONDS));
        metricTelemetry.setMax(meter.max(getBaseTimeUnit()));
        client.trackMetric(metricTelemetry);
        logger.info("sent timer to azure");
    }

    private void trackTimer(String meterName, Map<String, String> properties, FunctionTimer meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.totalTime(getBaseTimeUnit()));
        metricTelemetry.setCount((int)meter.count());
        client.trackMetric(metricTelemetry);
    }

    private void trackCounter(String meterName, Map<String, String> properties, Counter meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());

        //TODO : Verify this conversion
        metricTelemetry.setCount((int)Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }

    private void trackCounter(String meterName, Map<String, String> properties, FunctionCounter meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.count());

        //TODO : Verify this conversion
        metricTelemetry.setCount((int)Math.round(meter.count()));
        client.trackMetric(metricTelemetry);
    }

    private void trackGauge(String meterName, Map<String, String> properties, Gauge meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value());

        //Since the value of gauge is the value at the current instant and is send over aggregate period.
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

    private void trackGauge(String meterName, Map<String, String> properties, TimeGauge meter) {
        MetricTelemetry metricTelemetry = createAndGetBareBoneMetricTelemetry(meterName, properties);
        metricTelemetry.setValue(meter.value(getBaseTimeUnit()));

        //Since the value of gauge is the value at the current instant and is send over aggregate period.
        metricTelemetry.setCount(1);
        client.trackMetric(metricTelemetry);
    }

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
