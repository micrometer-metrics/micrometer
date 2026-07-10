/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.newrelic;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;

/**
 * Client provider for {@link NewRelicMeterRegistry}.
 *
 * @author Neil Powell
 * @since 1.4.0
 */
public interface NewRelicClientProvider {

    // long task timer
    String DURATION = "duration";

    String ACTIVE_TASKS = "activeTasks";

    // distribution summary & timer
    String MAX = "max";

    String TOTAL = "total";

    String AVG = "avg";

    String COUNT = "count";

    // timer
    String TOTAL_TIME = "totalTime";

    String TIME = "time";

    // gauge
    String VALUE = "value";

    // counter
    String THROUGHPUT = "throughput"; // TODO Why not "count"? ..confusing if just
                                      // counting something

    // timer
    String TIME_UNIT = "timeUnit";

    // all
    String METRIC_TYPE = "metricType";

    String METRIC_NAME = "metricName";

    default String getEventType(Meter.Id id, NewRelicConfig config, NamingConvention namingConvention) {
        if (config.meterNameEventTypeEnabled()) {
            // meter/metric name event type
            return id.getConventionName(namingConvention);
        }
        else {
            // static eventType "category"
            return config.eventType();
        }
    }

    void publish(NewRelicMeterRegistry meterRegistry);

    Object writeFunctionTimer(FunctionTimer timer);

    Object writeTimer(Timer timer);

    Object writeSummary(DistributionSummary summary);

    Object writeLongTaskTimer(LongTaskTimer timer);

    Object writeTimeGauge(TimeGauge gauge);

    Object writeGauge(Gauge gauge);

    Object writeCounter(Counter counter);

    Object writeFunctionCounter(FunctionCounter counter);

    Object writeMeter(Meter meter);

    /**
     * Set naming convention.
     * @param namingConvention naming convention
     * @since 1.4.2
     */
    void setNamingConvention(NamingConvention namingConvention);

}
