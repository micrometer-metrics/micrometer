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
package io.micrometer.influx.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.StringUtils;

import static java.util.stream.Collectors.joining;

/**
 * An Influx LineProtocol format serializer.
 *
 * @author Jakub Bednar (bednar@github) (13/05/2019 14:17)
 */
public class LineProtocolBuilder {

    private final TimeUnit baseTimeUnit;
    private final MeterRegistry.Config config;

    public LineProtocolBuilder(final TimeUnit baseTimeUnit, final MeterRegistry.Config config) {
        this.baseTimeUnit = baseTimeUnit;
        this.config = config;
    }

    public Stream<String> writeMeter(Meter m) {
        List<Field> fields = new ArrayList<>();
        for (Measurement measurement : m.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            String fieldKey = measurement.getStatistic().getTagValueRepresentation()
                    .replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
            fields.add(new Field(fieldKey, value));
        }
        if (fields.isEmpty()) {
            return Stream.empty();
        }
        Meter.Id id = m.getId();
        return Stream.of(influxLineProtocol(id, id.getType().name().toLowerCase(), fields.stream()));
    }

    public Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("active_tasks", timer.activeTasks()),
                new Field("duration", timer.duration(baseTimeUnit))
        );
        return Stream.of(influxLineProtocol(timer.getId(), "long_task_timer", fields));
    }

    public Stream<String> writeCounter(Counter counter) {
        Meter.Id id = counter.getId();
        double count = counter.count();

        return writeCounter(id, count);
    }

    public Stream<String> writeFunctionCounter(FunctionCounter counter) {
        Meter.Id id = counter.getId();
        double count = counter.count();

        return writeCounter(id, count);
    }

    public Stream<String> writeGauge(Gauge gauge) {
        Meter.Id id = gauge.getId();
        double value = gauge.value();

        return writeGauge(id, value);
    }

    public Stream<String> writeTimedGauge(TimeGauge timeGauge) {
        Meter.Id id = timeGauge.getId();
        double value = timeGauge.value(baseTimeUnit);

        if (Double.isFinite(value)) {
            return Stream.of(influxLineProtocol(id, "gauge", Stream.of(new Field("value", value))));
        }
        return Stream.empty();
    }

    public Stream<String> writeFunctionTimer(FunctionTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(baseTimeUnit)),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(baseTimeUnit))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    public Stream<String> writeTimer(Timer timer) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(baseTimeUnit)),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(baseTimeUnit)),
                new Field("upper", timer.max(baseTimeUnit))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    public Stream<String> writeSummary(DistributionSummary summary) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", summary.totalAmount()),
                new Field("count", summary.count()),
                new Field("mean", summary.mean()),
                new Field("upper", summary.max())
        );

        return Stream.of(influxLineProtocol(summary.getId(), "histogram", fields));
    }

    private Stream<String> writeCounter(Meter.Id id, double count) {

        if (Double.isFinite(count)) {
            return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
        }
        return Stream.empty();
    }

    private Stream<String> writeGauge(Meter.Id id, double value) {

        if (Double.isFinite(value)) {
            return Stream.of(influxLineProtocol(id, "gauge", Stream.of(new Field("value", value))));
        }
        return Stream.empty();
    }

    private String influxLineProtocol(Meter.Id id, String metricType, Stream<Field> fields) {
        String tags = id.getConventionTags(config.namingConvention()).stream()
                .filter(t -> StringUtils.isNotBlank(t.getValue()))
                .map(t -> "," + t.getKey() + "=" + t.getValue())
                .collect(joining(""));

        return id.getConventionName(config.namingConvention())
                + tags + ",metric_type=" + metricType + " "
                + fields.map(Field::toString).collect(joining(","))
                + " " + config.clock().wallTime();
    }
    
    public class Field {
        final String key;
        final double value;

        public Field(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }
}
