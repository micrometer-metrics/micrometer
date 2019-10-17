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
package io.micrometer.newrelic;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;

/**
 * @author Neil Powell
 */
public interface NewRelicClientProvider {
    //long task timer
    static final String DURATION = "duration";
    static final String ACTIVE_TASKS = "activeTasks";
    //distribution summary & timer
    static final String MAX = "max";
    static final String TOTAL = "total";
    static final String AVG = "avg";
    static final String COUNT = "count";
    //timer
    static final String TOTAL_TIME = "totalTime";
    static final String TIME = "time";
    //gauge
    static final String VALUE = "value";
    //counter
    static final String THROUGHPUT = "throughput";  //TODO Why not "count"? ..confusing if just counting something
    //timer
    static final String TIME_UNIT = "timeUnit";
    //all
    static final String METRIC_TYPE = "metricType";
    static final String METRIC_NAME = "metricName";

    default String getEventType(Meter.Id id, NewRelicConfig config, NamingConvention namingConvention) {
        String eventType = null;
        if (config.meterNameEventTypeEnabled()) {
            //meter/metric name event type
            eventType = id.getConventionName(namingConvention);
        } else {
            //static eventType "category"
            eventType = config.eventType();
        }
        return eventType;
    }

    void publish(NewRelicMeterRegistry meterRegistry, List<Meter> meters);

    Object writeFunctionTimer(FunctionTimer timer);

    Object writeTimer(Timer timer);

    Object writeSummary(DistributionSummary summary);

    Object writeLongTaskTimer(LongTaskTimer timer);

    Object writeTimeGauge(TimeGauge gauge);

    Object writeGauge(Gauge gauge);

    Object writeCounter(Counter counter);

    Object writeFunctionCounter(FunctionCounter counter);

    Object writeMeter(Meter meter);
}
