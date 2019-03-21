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
package io.micrometer.cloudwatch;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.*;
import io.micrometer.cloudwatch.aggregate.CloudWatchAggregateBuilder;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Dawid Kublik
 * @author Jon Schneider
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {
    private final CloudWatchConfig config;
    private final AmazonCloudWatchAsync amazonCloudWatchAsync;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);
    private final Collection<CloudWatchAggregateBuilder> aggregateBuilders = new CopyOnWriteArrayList<>();

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync) {
        this(config, clock, amazonCloudWatchAsync, Executors.defaultThreadFactory());
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync, ThreadFactory threadFactory) {
        super(config, clock);
        requireNonNull(config.namespace());

        this.amazonCloudWatchAsync = amazonCloudWatchAsync;
        this.config = config;
        this.config().namingConvention(NamingConvention.identity);
        start(threadFactory);
    }

    /**
     * Register a new metric aggregator.
     * <p>
     * 1. If a meter is not part of an aggregate, it will be published in full detail.
     * 2. If a meter is part of any aggregate such that {@code alsoPublishDetail = true}, then it will be published in full detail.
     * 3. Aggregates publish under a meter id that is equal to those set of tags that are common to all meters matching the search term. If you wish to drop
     * some of these tags JUST on the aggregate, use {@code additionalTagsToDrop}.
     * 4. If you wish to ignore tags even in detail representations, use {@link MeterFilter#ignoreTags(String...)}.
     * 5. If you wish to whitelist certain metrics for inclusion, use {@link MeterFilter#denyUnless(Predicate)}.
     *
     * @param search               The query used to build the aggregate.
     * @param alsoPublishDetail    Whether the detailed metrics rolled up by the aggregate should also be published.
     * @param additionalTagsToDrop Drop tags with these keys even if the tag value is the same across all matching meters.
     */
    public CloudWatchMeterRegistry aggregator(Search search, boolean alsoPublishDetail, String... additionalTagsToDrop) {
        aggregateBuilders.add(new CloudWatchAggregateBuilder(search, alsoPublishDetail, additionalTagsToDrop));
        return this;
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
        amazonCloudWatchAsync.putMetricDataAsync(putMetricDataRequest, new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
            @Override
            public void onError(Exception exception) {
                logger.error("Error sending metric data.", exception);
            }

            @Override
            public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
                logger.debug("Published metric with namespace:{}", request.getNamespace());
            }
        });
    }

    private List<MetricDatum> metricData() {
        Collection<Meter> detailMetrics = aggregateBuilders.stream().filter(CloudWatchAggregateBuilder::isAlsoPublishDetail)
                .flatMap(agg -> agg.getSearch().meters().stream())
                .collect(Collectors.toMap(Meter::getId, Function.identity()))
                .values();

        return Stream.concat(
                aggregateBuilders.stream().flatMap(CloudWatchAggregateBuilder::aggregates),
                detailMetrics.stream()
        ).flatMap(m -> {
            if (m instanceof Timer) {
                return metricData((Timer) m);
            }
            if (m instanceof DistributionSummary) {
                return metricData((DistributionSummary) m);
            }
            if (m instanceof FunctionTimer) {
                return metricData((FunctionTimer) m);
            }
            return metricData(m);
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
            metrics.add(metricDatum(idWithSuffix(timer.getId(), DoubleFormat.decimalOrNan(v.percentile()) + "percentile"),
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
            metrics.add(metricDatum(idWithSuffix(summary.getId(), DoubleFormat.decimalOrNan(v.percentile()) + "percentile"),
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

    private StandardUnit toStandardUnit(@Nullable String unit) {
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

    private List<Dimension> toDimensions(List<Tag> tags) {
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
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return idWithSuffixAndUnit(id, suffix, id.getBaseUnit());
    }

    /**
     * Copy tags and description from an existing id, but change the name and unit.
     */
    private Meter.Id idWithSuffixAndUnit(Meter.Id id, String suffix, @Nullable String unit) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), unit, id.getDescription(), id.getType());
    }
}
