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

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AzureMeterRegistry extends StepMeterRegistry {
    private final TelemetryClient client;

    public AzureMeterRegistry(StepRegistryConfig config, Clock clock) {
        super(config, clock);

        config().namingConvention(new AzureNamingConvention());

        TelemetryConfiguration clientConfig = new TelemetryConfiguration();
//        clientConfig.setInstrumentationKey("");

        this.client = new TelemetryClient();

    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            Meter.Id id = meter.getId();
            String name = getConventionName(id);
            Map<String, String> properties = getConventionTags(id).stream()
                    .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

            if (meter instanceof TimeGauge) {
                client.trackMetric(name, ((TimeGauge) meter).value(getBaseTimeUnit()));
            } else if (meter instanceof Gauge) {
                client.trackMetric(name, ((Gauge) meter).value());
            } else if (meter instanceof Counter) {
                // FIXME how do we ship tags for non-timer types?
                client.trackMetric(name, ((Counter) meter).count());
            } else if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                // FIXME what does value represent?
//                client.trackMetric(name, VALUE?, timer.count(),
//                        timer.percentile(0, getBaseTimeUnit()),
//                        timer.max(getBaseTimeUnit()),
//                        properties);
            } else if (meter instanceof DistributionSummary) {
                // this will look very similar to Timer
            } else if (meter instanceof LongTaskTimer) {
                LongTaskTimer longTaskTimer = (LongTaskTimer) meter;
                client.trackMetric(getConventionName(id, "active"), longTaskTimer.activeTasks());
                client.trackMetric(getConventionName(id, "duration"), longTaskTimer.duration(getBaseTimeUnit()));
            }
            // TODO FunctionCounter, FunctionTimer
//            else
        }
    }

    private String getConventionName(Meter.Id id, String suffix) {
        return config().namingConvention()
                .name(id.getName() + "." + suffix, id.getType(), id.getBaseUnit());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
