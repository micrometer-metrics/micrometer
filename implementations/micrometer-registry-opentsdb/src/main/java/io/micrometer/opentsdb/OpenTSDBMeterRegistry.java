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
package io.micrometer.opentsdb;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.distribution.FixedBoundaryVictoriaMetricsHistogram.getRangeTagValue;
import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

/**
 * Default naming conventions are optimized to be Prometheus compatible.
 *
 * @author Nikolay Ustinov
 * @since 1.4.0
 */
public class OpenTSDBMeterRegistry extends PushMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("opentsdb-metrics-publisher");

    private final OpenTSDBConfig config;

    private final HttpSender httpClient;

    private final Logger logger = LoggerFactory.getLogger(OpenTSDBMeterRegistry.class);

    @SuppressWarnings("deprecation")
    public OpenTSDBMeterRegistry(OpenTSDBConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    public OpenTSDBMeterRegistry(OpenTSDBConfig config, Clock clock, ThreadFactory threadFactory,
            HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new OpenTSDBNamingConvention());
        this.config = config;
        this.httpClient = httpClient;

        start(threadFactory);
    }

    public static Builder builder(OpenTSDBConfig config) {
        return new Builder(config);
    }

    /**
     * Convert a double to its string representation in Go.
     */
    private static String doubleToGoString(double d) {
        if (d == Double.POSITIVE_INFINITY || d == Double.MAX_VALUE || d == Long.MAX_VALUE) {
            return "+Inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(d)) {
            return "NaN";
        }
        return Double.toString(d);
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id);
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new OpenTSDBDistributionSummary(id, clock, distributionStatisticConfig, scale, config.flavor());
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return new OpenTSDBTimer(id, clock, distributionStatisticConfig, pauseDetector, config.flavor());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit,
                getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CumulativeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(config.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                httpClient.post(config.uri())
                    .withBasicAuthentication(config.userName(), config.password())
                    .withJsonContent(batch.stream()
                        .flatMap(m -> m.match(this::writeGauge, this::writeCounter, this::writeTimer,
                                this::writeSummary, this::writeLongTaskTimer, this::writeTimeGauge,
                                this::writeFunctionCounter, this::writeFunctionTimer, this::writeCustomMetric))
                        .collect(Collectors.joining(",", "[", "]")))
                    .compress()
                    .send()
                    .onSuccess(response -> logger.debug("successfully sent {} metrics to opentsdb.", batch.size()))
                    .onError(response -> logger.error("failed to send metrics to opentsdb: {}", response.body()));
            }
            catch (Throwable t) {
                logger.warn("failed to send metrics to opentsdb", t);
            }
        }
    }

    Stream<String> writeSummary(DistributionSummary summary) {
        long wallTime = config().clock().wallTime();

        final ValueAtPercentile[] percentileValues = summary.takeSnapshot().percentileValues();
        final CountAtBucket[] histogramCounts = ((OpenTSDBDistributionSummary) summary).histogramCounts();
        double count = summary.count();

        List<String> metrics = new ArrayList<>();

        metrics.add(writeMetricWithSuffix(summary.getId(), "count", wallTime, count));
        metrics.add(writeMetricWithSuffix(summary.getId(), "sum", wallTime, summary.totalAmount()));
        metrics.add(writeMetricWithSuffix(summary.getId(), "max", wallTime, summary.max()));

        if (percentileValues.length > 0) {
            metrics.addAll(writePercentiles(summary, wallTime, percentileValues));
        }

        if (histogramCounts.length > 0) {
            metrics.addAll(writeHistogram(wallTime, summary, histogramCounts, count, getBaseTimeUnit()));
        }

        return metrics.stream();
    }

    Stream<String> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = config().clock().wallTime();

        return Stream.of(writeMetricWithSuffix(timer.getId(), "count", wallTime, timer.count()),
                // not applicable
                // writeMetricWithSuffix(timer.getId(), "avg", wallTime,
                // timer.mean(getBaseTimeUnit())),
                writeMetricWithSuffix(timer.getId(), "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    Stream<String> writeTimer(Timer timer) {
        long wallTime = config().clock().wallTime();

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        final ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
        final CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
        double count = timer.count();

        List<String> metrics = new ArrayList<>();

        metrics.add(writeMetricWithSuffix(timer.getId(), "count", wallTime, count));
        metrics.add(writeMetricWithSuffix(timer.getId(), "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
        metrics.add(writeMetricWithSuffix(timer.getId(), "max", wallTime, timer.max(getBaseTimeUnit())));

        if (percentileValues.length > 0) {
            metrics.addAll(writePercentiles(timer, wallTime, percentileValues));
        }

        if (histogramCounts.length > 0) {
            metrics.addAll(writeHistogram(wallTime, timer, histogramCounts, count, getBaseTimeUnit()));
        }

        return metrics.stream();
    }

    private List<String> writePercentiles(Meter meter, long wallTime, ValueAtPercentile[] percentileValues) {
        List<String> metrics = new ArrayList<>(percentileValues.length);

        boolean forTimer = meter instanceof Timer;
        // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
        for (ValueAtPercentile v : percentileValues) {
            metrics
                .add(writeMetric(meter.getId().withTag(new ImmutableTag("quantile", doubleToGoString(v.percentile()))),
                        wallTime, (forTimer ? v.value(getBaseTimeUnit()) : v.value())));
        }

        return metrics;
    }

    private List<String> writeHistogram(long wallTime, Meter meter, CountAtBucket[] histogramCounts, double count,
            @Nullable TimeUnit timeUnit) {
        List<String> metrics = new ArrayList<>(histogramCounts.length);

        if (config.flavor() == null) {
            // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram,
            // which is at least SOME standard
            // histogram format to follow
            for (CountAtBucket c : histogramCounts) {
                metrics.add(writeMetricWithSuffix(
                        meter.getId()
                            .withTag(new ImmutableTag("le",
                                    doubleToGoString(timeUnit == null ? c.bucket() : c.bucket(timeUnit)))),
                        "bucket", wallTime, c.count()));
            }

            // the +Inf bucket should always equal `count`
            metrics.add(writeMetricWithSuffix(meter.getId().withTag(new ImmutableTag("le", "+Inf")), "bucket", wallTime,
                    count));
        }
        else if (OpenTSDBFlavor.VictoriaMetrics.equals(config.flavor())) {
            for (CountAtBucket c : histogramCounts) {
                metrics.add(writeMetricWithSuffix(
                        meter.getId()
                            .withTag(Tag.of("vmrange",
                                    getRangeTagValue(timeUnit == null ? c.bucket() : c.bucket(timeUnit)))),
                        "bucket", wallTime, c.count()));
            }
        }

        return metrics;
    }

    // VisibleForTesting
    Stream<String> writeFunctionCounter(FunctionCounter counter) {
        double count = counter.count();
        if (Double.isFinite(count)) {
            return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), count));
        }
        return Stream.empty();
    }

    Stream<String> writeCounter(Counter counter) {
        return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), counter.count()));
    }

    // VisibleForTesting
    Stream<String> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(gauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeTimeGauge(TimeGauge timeGauge) {
        double value = timeGauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(timeGauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        long wallTime = config().clock().wallTime();

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        final ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
        final CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
        double count = timer.activeTasks();

        List<String> metrics = new ArrayList<>();

        metrics.add(writeMetricWithSuffix(timer.getId(), "active.count", wallTime, count));
        metrics.add(writeMetricWithSuffix(timer.getId(), "duration.sum", wallTime, timer.duration(getBaseTimeUnit())));
        metrics.add(writeMetricWithSuffix(timer.getId(), "max", wallTime, timer.max(getBaseTimeUnit())));

        if (percentileValues.length > 0) {
            metrics.addAll(writePercentiles(timer, wallTime, percentileValues));
        }

        if (histogramCounts.length > 0) {
            metrics.addAll(writeHistogram(wallTime, timer, histogramCounts, count, getBaseTimeUnit()));
        }

        return metrics.stream();
    }

    private Stream<String> writeCustomMetric(Meter meter) {
        long wallTime = config().clock().wallTime();

        List<Tag> tags = getConventionTags(meter.getId());

        return StreamSupport.stream(meter.measure().spliterator(), false).map(ms -> {
            Tags localTags = Tags.concat(tags, "statistics", ms.getStatistic().toString());
            String name = getConventionName(meter.getId());

            switch (ms.getStatistic()) {
                case TOTAL:
                case TOTAL_TIME:
                    name += ".sum";
                    break;
                case MAX:
                    name += ".max";
                    break;
                case ACTIVE_TASKS:
                    name += ".active.count";
                    break;
                case DURATION:
                    name += ".duration.sum";
                    break;
            }

            return new OpenTSDBMetricBuilder().field("metric", name)
                .datapoints(wallTime, ms.getValue())
                .tags(localTags)
                .build();
        });
    }

    String writeMetricWithSuffix(Meter.Id id, String suffix, long wallTime, double value) {
        // usually tagKeys and metricNames naming rules are the same
        // but we can't call getConventionName again after adding suffix
        return new OpenTSDBMetricBuilder()
            .field("metric",
                    suffix.isEmpty() ? getConventionName(id)
                            : config().namingConvention().tagKey(getConventionName(id) + "." + suffix))
            .datapoints(wallTime, value)
            .tags(getConventionTags(id))
            .build();
    }

    String writeMetric(Meter.Id id, long wallTime, double value) {
        return writeMetricWithSuffix(id, "", wallTime, value);
    }

    private static class OpenTSDBMetricBuilder {

        private final StringBuilder sb = new StringBuilder("{");

        OpenTSDBMetricBuilder field(String key, String value) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('\"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('\"');
            return this;
        }

        OpenTSDBMetricBuilder datapoints(long wallTime, double value) {
            sb.append(",\"timestamp\":")
                .append(wallTime)
                .append(",\"value\":")
                .append(DoubleFormat.wholeOrDecimal(value));
            return this;
        }

        OpenTSDBMetricBuilder tags(Iterable<Tag> tags) {
            OpenTSDBMetricBuilder tagBuilder = new OpenTSDBMetricBuilder();
            if (!tags.iterator().hasNext()) {
                // tags field is required for OpenTSDB, use hostname as a default tag
                try {
                    tagBuilder.field("host", InetAddress.getLocalHost().getHostName());
                }
                catch (UnknownHostException ignore) {
                    /* ignore */
                }
            }
            else {
                for (Tag tag : tags) {
                    tagBuilder.field(tag.getKey(), tag.getValue());
                }
            }

            sb.append(",\"tags\":").append(tagBuilder.build());
            return this;
        }

        String build() {
            return sb.append('}').toString();
        }

    }

    public static class Builder {

        private final OpenTSDBConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(OpenTSDBConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public OpenTSDBMeterRegistry build() {
            return new OpenTSDBMeterRegistry(config, clock, threadFactory, httpClient);
        }

    }

}
