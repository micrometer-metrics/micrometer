/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.timestream2;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteAsyncClient;
import software.amazon.awssdk.services.timestreamwrite.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static software.amazon.awssdk.services.timestreamwrite.model.TimeUnit.SECONDS;

/**
 * {@link MeterRegistry} for TimeStream.
 *
 * @author Guillaume Hiron
 * @since 1.6.0
 */
public class TimestreamMeterRegistry extends StepMeterRegistry {

    static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("timestream-metrics-publisher");

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(TimestreamMeterRegistry.class);

    private final Logger logger = LoggerFactory.getLogger(TimestreamMeterRegistry.class);

    private final TimestreamConfig config;

    private final TimestreamWriteAsyncClient timestreamWriteAsyncClient;

    /**
     * Create a new instance with given parameters.
     *
     * @param config        configuration to use
     * @param clock         clock to use
     * @param threadFactory thread factory to use
     * @param client        TimeStream client to use
     */
    //VisibleForTesting
    TimestreamMeterRegistry(TimestreamConfig config, Clock clock, ThreadFactory threadFactory, TimestreamWriteAsyncClient client) {
        super(config, clock);
        config().namingConvention(new TimestreamNamingConvention());
        this.config = config;
        checkClient(client);
        this.timestreamWriteAsyncClient = client;
        start(threadFactory);
    }

    private void checkClient(TimestreamWriteAsyncClient client) {
        Objects.requireNonNull(client);
    }

    @Override
    protected void publish() {
        MeterPartition.partition(this, config.batchSize())
                .stream()
                .map(this::toRecords)
                .forEach(this::sendRecords);
    }

    //VisibleForTesting
    List<Record> toRecords(List<Meter> partition) {
        Batch batch = new Batch();
        return partition.stream().flatMap(m -> m.match(
                batch::gaugeData,
                batch::counterData,
                batch::timerData,
                batch::summaryData,
                batch::longTaskTimerData,
                batch::timeGaugeData,
                batch::functionCounterData,
                batch::functionTimerData,
                batch::metricData)
        ).collect(toList());
    }

    //VisibleForTesting
    void sendRecords(List<Record> records) {
        WriteRecordsRequest writeRecordsRequest = WriteRecordsRequest.builder()
                .databaseName(config.databaseName())
                .tableName(config.tableName())
                .records(records)
                .build();

        timestreamWriteAsyncClient
                .writeRecords(writeRecordsRequest)
                .thenRun(() ->
                        logger.debug("published records to database: {}, table: {} ", writeRecordsRequest.databaseName(), writeRecordsRequest.tableName())
                )
                .exceptionally(throwable -> {
                    if (throwable instanceof AbortedException) {
                        logger.warn("sending records data was aborted: " + throwable.getMessage(), throwable);
                    } else {
                        logger.error("error sending records data.", throwable);
                    }
                    return null;
                });
    }

    public static Builder builder() {
        return new Builder().config(key -> null);
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private TimestreamConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private TimestreamWriteAsyncClient client;

        private Builder() {
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder config(TimestreamConfig config) {
            this.config = config;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder client(TimestreamWriteAsyncClient client) {
            this.client = client;
            return this;
        }

        public TimestreamMeterRegistry build() {
            return new TimestreamMeterRegistry(config, clock, threadFactory, client);
        }
    }

    class Batch {
        private final Instant timestamp = Instant.ofEpochMilli(clock.wallTime());

        private Stream<Record> gaugeData(Gauge gauge) {
            Record record = record(gauge.getId(), "value", MeasureValueType.DOUBLE, gauge.value());
            if (record == null) {
                return Stream.empty();
            }
            return Stream.of(record);
        }

        private Stream<Record> counterData(Counter counter) {
            return Stream.of(record(counter.getId(), "count", MeasureValueType.BIGINT, counter.count()));
        }

        // VisibleForTesting
        Stream<Record> timerData(Timer timer) {
            Stream.Builder<Record> metrics = Stream.builder();
            metrics.add(record(timer.getId(), "sum", MeasureValueType.DOUBLE, timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            metrics.add(record(timer.getId(), "count", MeasureValueType.BIGINT, count));
            if (count > 0) {
                metrics.add(record(timer.getId(), "avg", MeasureValueType.DOUBLE, timer.mean(getBaseTimeUnit())));
                metrics.add(record(timer.getId(), "max", MeasureValueType.DOUBLE, timer.max(getBaseTimeUnit())));
            }
            return metrics.build();
        }

        // VisibleForTesting
        Stream<Record> summaryData(DistributionSummary summary) {
            Stream.Builder<Record> metrics = Stream.builder();
            metrics.add(record(summary.getId(), "sum", MeasureValueType.DOUBLE, summary.totalAmount()));
            long count = summary.count();
            metrics.add(record(summary.getId(), "count", MeasureValueType.BIGINT, count));
            if (count > 0) {
                metrics.add(record(summary.getId(), "avg", MeasureValueType.DOUBLE, summary.mean()));
                metrics.add(record(summary.getId(), "max", MeasureValueType.DOUBLE, summary.max()));
            }
            return metrics.build();
        }

        private Stream<Record> longTaskTimerData(LongTaskTimer longTaskTimer) {
            return Stream.of(
                    record(longTaskTimer.getId(), "activeTasks", MeasureValueType.BIGINT, longTaskTimer.activeTasks()),
                    record(longTaskTimer.getId(), "duration", MeasureValueType.DOUBLE, longTaskTimer.duration(getBaseTimeUnit())));
        }

        private Stream<Record> timeGaugeData(TimeGauge gauge) {
            Record metricDatum = record(gauge.getId(), "value", MeasureValueType.DOUBLE, gauge.value(getBaseTimeUnit()));
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<Record> functionCounterData(FunctionCounter counter) {
            Record metricDatum = record(counter.getId(), "count", MeasureValueType.BIGINT, counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<Record> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer
            double sum = timer.totalTime(getBaseTimeUnit());
            if (!Double.isFinite(sum)) {
                return Stream.empty();
            }
            Stream.Builder<Record> metrics = Stream.builder();
            double count = timer.count();
            metrics.add(record(timer.getId(), "count", MeasureValueType.BIGINT, count));
            metrics.add(record(timer.getId(), "sum", MeasureValueType.DOUBLE, sum));
            if (count > 0) {
                metrics.add(record(timer.getId(), "avg", MeasureValueType.DOUBLE, timer.mean(getBaseTimeUnit())));
            }
            return metrics.build();
        }

        // VisibleForTesting
        Stream<Record> metricData(Meter m) {
            return stream(m.measure().spliterator(), false)
                    .map(ms -> record(m.getId().withTag(ms.getStatistic()),
                            null,
                            MeasureValueType.DOUBLE, //default type ?
                            ms.getValue()))
                    .filter(Objects::nonNull);
        }

        @Nullable
        private Record record(Meter.Id id, @Nullable String suffix, MeasureValueType measureValueType, double value) {
            if (Double.isNaN(value)) {
                return null;
            }
            List<Tag> tags = id.getConventionTags(config().namingConvention());
            return Record.builder()
                    .measureName(getMetricName(id, suffix))
                    .measureValue(String.valueOf(clampMetricValue(value)))
                    .measureValueType(measureValueType)
                    .dimensions(toDimensions(tags))
                    .time(String.valueOf(timestamp.getEpochSecond()))
                    .timeUnit(SECONDS)
                    .build();
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .filter(this::isAcceptableTag)
                    .map(tag -> Dimension.builder()
                            .name(tag.getKey())
                            .value(tag.getValue())
                            .dimensionValueType(DimensionValueType.VARCHAR)
                            .build()
                    )
                    .collect(toList());
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                warnThenDebugLogger.log("Dropping a tag with key '" + tag.getKey() + "' because its value is blank.");
                return false;
            }
            return true;
        }

        /**
         * Clean up metric to be within the allowable range}
         *
         * @param value unsanitized value
         * @return value clamped to allowable range
         */
        double clampMetricValue(double value) {
            // Leave as is and let the SDK reject it
            if (Double.isNaN(value)) {
                return value;
            }
            double magnitude = Math.abs(value);
            if (magnitude == 0) {
                // Leave zero as zero
                return 0;
            }
            // Non-zero magnitude, clamp to allowed range
            double clampedMag = Math.min(magnitude, Double.MAX_VALUE);
            return Math.copySign(clampedMag, value);
        }

    }
}
