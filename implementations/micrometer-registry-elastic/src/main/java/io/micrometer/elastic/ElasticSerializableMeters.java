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
package io.micrometer.elastic;

import io.micrometer.core.instrument.*;

class ElasticSerializableMeters {
    public static class ElasticSerializableMeter<T extends Meter> {
        private final T meter;
        private final long timestamp;
        private final String type;

        ElasticSerializableMeter(T meter, long timestamp, String type) {
            this.meter = meter;
            this.timestamp = timestamp;
            this.type = type;
        }

        public T getMeter() {
            return meter;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getType() {
            return type;
        }
    }

    static class ElasticTimer extends ElasticSerializableMeter<Timer> {
        ElasticTimer(Timer meter, long timestamp) {
            super(meter, timestamp, "timer");
        }
    }

    static class ElasticFunctionTimer extends ElasticSerializableMeter<FunctionTimer> {
        ElasticFunctionTimer(FunctionTimer meter, long timestamp) {
            super(meter, timestamp, "timer");
        }
    }

    static class ElasticDistributionSummary extends ElasticSerializableMeter<DistributionSummary> {
        ElasticDistributionSummary(DistributionSummary meter, long timestamp) {
            super(meter, timestamp, "histogram");
        }
    }

    static class ElasticLongTaskTimer extends ElasticSerializableMeter<LongTaskTimer> {
        ElasticLongTaskTimer(LongTaskTimer meter, long timestamp) {
            super(meter, timestamp, "long_task_timer");
        }
    }

    static class ElasticCounter extends ElasticSerializableMeter<Counter> {
        ElasticCounter(Counter meter, long timestamp) {
            super(meter, timestamp, "counter");
        }
    }

    static class ElasticFunctionCounter extends ElasticSerializableMeter<FunctionCounter> {
        ElasticFunctionCounter(FunctionCounter meter, long timestamp) {
            super(meter, timestamp, "counter");
        }
    }

    static class ElasticGauge extends ElasticSerializableMeter<Gauge> {

        ElasticGauge(Gauge meter, long timestamp) {
            super(meter, timestamp, "gauge");
        }
    }

    static class ElasticTimeGauge extends ElasticSerializableMeter<TimeGauge> {

        ElasticTimeGauge(TimeGauge meter, long timestamp) {
            super(meter, timestamp, "gauge");
        }
    }

    static class ElasticMeter extends ElasticSerializableMeter<Meter> {
        ElasticMeter(Meter meter, long timestamp) {
            super(meter, timestamp, "unknown");
        }
    }
}
