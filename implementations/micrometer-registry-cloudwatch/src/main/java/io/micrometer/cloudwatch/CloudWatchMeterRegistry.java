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
package io.micrometer.cloudwatch;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.HistogramSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.ValueAtPercentile;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Send metrics to CloudWatch
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {
    private final CloudWatchConfig config;
    private final AmazonCloudWatchAsync amazonCloudWatchAsync;
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock, AmazonCloudWatchAsync amazonCloudWatchAsync) {
        super(config, clock);
        this.amazonCloudWatchAsync = amazonCloudWatchAsync;
        this.config = config;
        this.config().namingConvention(NamingConvention.identity);

        logger.debug("Using batch size of {}", config.batchSize());
        
        if (config.dryRun()) {
            logger.warn("DRY RUN enabled for CloudWatchMeterRegistry. Metrics will be printed to the DEBUG log");
        }
        start();
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, AmazonCloudWatchAsync amazonCloudWatchAsync) {
        this(config, Clock.SYSTEM, amazonCloudWatchAsync);
    }

    @Override
    protected void publish() {
        for (List<MetricDatum> batch : MetricDatumPartition.partition(metricData(), config.batchSize())) {
            sendMetricData(batch);
        }
    }

    private void sendMetricData(List<MetricDatum> metricData) {
        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace(config.namespace())
                .withMetricData(metricData);
        if (config.dryRun()) {
           logger.debug("DRY RUN - Would send PutMetricDataRequest: {}", putMetricDataRequest);
        } else {
            amazonCloudWatchAsync.putMetricDataAsync(putMetricDataRequest,
                                                     new PutMetricDataResultLogger());
        }
    }

    private List<MetricDatum> metricData() {
        return getMeters().stream().flatMap(m -> {
            if (m instanceof Timer) {
                return metricData((Timer) m);
            } else if (m instanceof DistributionSummary) {
                return metricData((DistributionSummary) m);
            } else if (m instanceof FunctionTimer) {
                return metricData((FunctionTimer) m);
            } else {
                return metricData(m);
            }
        }).collect(toList());
    }

    private Stream<MetricDatum> metricData(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
                metricDatum(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                metricDatum(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())));
    }

    private Stream<MetricDatum> metricData(Timer timer) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);
        final Stream.Builder<MetricDatum> metrics = Stream.builder();

        metrics.add(metricDatum(idWithSuffixAndUnit(timer.getId(), "sum", getBaseTimeUnit().name()), wallTime, snapshot.total(getBaseTimeUnit())));
        metrics.add(metricDatum(idWithSuffixAndUnit(timer.getId(), "count", "count"), wallTime, snapshot.count()));
        metrics.add(metricDatum(idWithSuffixAndUnit(timer.getId(), "avg", getBaseTimeUnit().name()), wallTime, snapshot.mean(getBaseTimeUnit())));
        metrics.add(metricDatum(idWithSuffixAndUnit(timer.getId(), "max", getBaseTimeUnit().name()), wallTime, snapshot.max(getBaseTimeUnit())));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            metrics.add(metricDatum(idWithSuffix(timer.getId(), percentileFormat.format(v.percentile()) + "percentile"),
                    wallTime, v.value(getBaseTimeUnit())));
        }

        return metrics.build();
    }

    private Stream<MetricDatum> metricData(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);
        final Stream.Builder<MetricDatum> metrics = Stream.builder();

        metrics.add(metricDatum(idWithSuffix(summary.getId(), "sum"), wallTime, snapshot.total()));
        metrics.add(metricDatum(idWithSuffix(summary.getId(), "count"), wallTime, snapshot.count()));
        metrics.add(metricDatum(idWithSuffix(summary.getId(), "avg"), wallTime, snapshot.mean()));
        metrics.add(metricDatum(idWithSuffix(summary.getId(), "max"), wallTime, snapshot.max()));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            metrics.add(metricDatum(idWithSuffix(summary.getId(), percentileFormat.format(v.percentile()) + "percentile"),
                    wallTime, v.value()));
        }

        return metrics.build();
    }

    private Stream<MetricDatum> metricData(Meter m) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
                .map(ms -> metricDatum(m.getId().withTag(ms.getStatistic()), wallTime, ms.getValue()));
    }

    private MetricDatum metricDatum(Meter.Id id, long wallTime, double value) {
        String metricName = id.getConventionName(config().namingConvention());
        List<Tag> tags = id.getConventionTags(config().namingConvention());
        
        return new MetricDatum()
                .withMetricName(metricName)
                .withDimensions(toDimensions(tags))
                .withTimestamp(new Date(wallTime))
                .withValue(CloudWatchUtils.clampMetricValue(value))
                .withUnit(toStandardUnit(id.getBaseUnit()));
    }

    private static StandardUnit toStandardUnit(String unit) {
        if (unit == null) {
            return StandardUnit.None;
        }
        switch (unit.toLowerCase()) {
            case "bytes":
                return StandardUnit.Bytes;
            case "milliseconds":
                return StandardUnit.Milliseconds;
            case "count":
                return StandardUnit.Count;
        }
        return StandardUnit.None;
    }


    private static List<Dimension> toDimensions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new Dimension().withName(tag.getKey()).withValue(tag.getValue()))
                .collect(toList());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private static Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return idWithSuffixAndUnit(id, suffix, id.getBaseUnit());
    }

    /**
     * Copy tags and description from an existing id, but change the name and unit.
     */
    private static Meter.Id idWithSuffixAndUnit(Meter.Id id, String suffix, String unit) {
        return new Meter.Id(id.getName() + '.' + suffix, id.getTags(), unit, id.getDescription(), id.getType());
    }

    /**
     * Logs result of PutMetricDataRequest
     */
    private class PutMetricDataResultLogger implements AsyncHandler<PutMetricDataRequest, PutMetricDataResult> {
        @Override
        public void onError(Exception exception) {
            logger.error("Error sending metric data.", exception);
        }

        @Override
        public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
            logger.debug("Published metric with namespace:{}", request.getNamespace());
        }
    }
}
