/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.prometheusmetrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.metrics.model.registry.MetricType;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.ClassicHistogramBuckets;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Exemplar;
import io.prometheus.metrics.model.snapshots.Exemplars;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot.InfoDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@link MultiCollector} for Micrometer. It maps all meters that share a Prometheus name
 * to the metric families of that name: each meter is added as a {@link Child} that
 * contributes data points to one or more families, and each family is exposed as a single
 * {@link MetricSnapshot} on collection.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
class MicrometerCollector implements MultiCollector {

    private final Map<Meter.Id, Child> children = new ConcurrentHashMap<>();

    private final Map<String, MetricFamilyDescriptor> descriptorsByFamilyName = new ConcurrentHashMap<>();

    final String conventionName;

    // the id of the meter used to create this MicrometerCollector
    private final Meter.Id originalMeterId;

    // take name to avoid calling NamingConvention#name after the call-site has already
    // done it
    MicrometerCollector(String name, Meter.Id id) {
        this.conventionName = name;
        this.originalMeterId = id;
    }

    void addCounter(MeterContext context, PrometheusCounter counter) {
        MetricFamilyDescriptor family = familyDescriptor(MetricType.COUNTER, conventionName, context.tagKeys,
                context.help);
        addSingleDataPoint(context, family, () -> new CounterDataPointSnapshot(counter.count(), context.labels,
                counter.exemplar(), context.createdTimestampMillis));
    }

    void addFunctionCounter(MeterContext context, FunctionCounter functionCounter) {
        MetricFamilyDescriptor family = familyDescriptor(MetricType.COUNTER, conventionName, context.tagKeys,
                context.help);
        addSingleDataPoint(context, family, () -> new CounterDataPointSnapshot(functionCounter.count(), context.labels,
                null, context.createdTimestampMillis));
    }

    void addGauge(MeterContext context, Gauge gauge) {
        if (context.id.getName().endsWith(".info")) {
            MetricFamilyDescriptor family = familyDescriptor(MetricType.INFO, conventionName, context.tagKeys,
                    context.help);
            addSingleDataPoint(context, family, () -> new InfoDataPointSnapshot(context.labels));
        }
        else {
            MetricFamilyDescriptor family = familyDescriptor(MetricType.GAUGE, conventionName, context.tagKeys,
                    context.help);
            addSingleDataPoint(context, family, () -> new GaugeDataPointSnapshot(gauge.value(), context.labels, null));
        }
    }

    void addFunctionTimer(MeterContext context, FunctionTimer functionTimer) {
        MetricFamilyDescriptor family = familyDescriptor(MetricType.SUMMARY, conventionName, context.tagKeys,
                context.help);
        addSingleDataPoint(context, family,
                () -> new SummaryDataPointSnapshot((long) functionTimer.count(), functionTimer.totalTime(SECONDS),
                        Quantiles.EMPTY, context.labels, null, context.createdTimestampMillis));
    }

    void addTimer(MeterContext context, PrometheusTimer timer) {
        addDistribution(context, timer, () -> exemplarsInSeconds(timer.exemplars()), SECONDS);
    }

    void addLongTaskTimer(MeterContext context, LongTaskTimer longTaskTimer) {
        addDistribution(context, longTaskTimer, () -> Exemplars.EMPTY, SECONDS);
    }

    void addDistributionSummary(MeterContext context, PrometheusDistributionSummary summary) {
        // a distribution summary records unit-less values, so no conversion is needed
        addDistribution(context, summary, summary::exemplars, null);
    }

    /**
     * Adds a meter with distribution statistics, i.e. a timer, long task timer, or
     * distribution summary. It contributes a data point to the primary family, which is a
     * summary or a histogram depending on whether the meter publishes buckets, and one to
     * the {@code _max} gauge family.
     * @param timeUnit if {@code null}, snapshot values are used as-is; otherwise
     * time-based snapshot values are converted to this unit
     */
    private void addDistribution(MeterContext context, HistogramSupport histogramSupport,
            Supplier<Exemplars> exemplarsSupplier, @Nullable TimeUnit timeUnit) {
        MetricType primaryType = histogramSupport.takeSnapshot().histogramCounts().length == 0 ? MetricType.SUMMARY
                : MetricType.HISTOGRAM;
        MetricFamilyDescriptor family = familyDescriptor(primaryType, conventionName, context.tagKeys, context.help);
        MetricFamilyDescriptor maxFamily = familyDescriptor(MetricType.GAUGE, conventionName + "_max", context.tagKeys,
                context.help);

        add(context.id, samples -> {
            io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot = histogramSupport.takeSnapshot();
            CountAtBucket[] histogramCounts = snapshot.histogramCounts();
            long count = snapshot.count();
            double sum = timeUnit == null ? snapshot.total() : snapshot.total(timeUnit);

            if (histogramCounts.length == 0) {
                samples.accept(family,
                        new SummaryDataPointSnapshot(count, sum, quantiles(snapshot.percentileValues(), timeUnit),
                                context.labels, exemplarsSupplier.get(), context.createdTimestampMillis));
            }
            else {
                samples.accept(family,
                        new HistogramDataPointSnapshot(nonCumulativeBuckets(histogramCounts, count, timeUnit), sum,
                                context.labels, exemplarsSupplier.get(), context.createdTimestampMillis));

                // TODO: Add support back for VictoriaMetrics
                // Previously we had low-level control so a histogram was just
                // a bunch of Collector.MetricFamilySamples.Sample
                // that has an le label for Prometheus and a vmrange label for
                // Victoria.
                // That control is gone now, so we don’t have control over the
                // output and when HistogramDataPointSnapshot is written, the
                // bucket name is hardcoded to le.
            }

            double max = timeUnit == null ? snapshot.max() : snapshot.max(timeUnit);
            samples.accept(maxFamily, new GaugeDataPointSnapshot(max, context.labels, null));
        });
    }

    /**
     * Adds a custom meter. Unlike the other meters, the families it contributes to depend
     * on the statistics of its measurements, it can contribute more than one data point
     * to the same family, and it adds a {@code statistic} label of its own.
     */
    void addCustomMeter(MeterContext context, Iterable<Measurement> measurements) {
        List<String> statKeys = new ArrayList<>(context.tagKeys);
        statKeys.add("statistic");

        // precompute the family and labels per statistic; statistics that map to the
        // same Prometheus name (e.g. TOTAL and TOTAL_TIME) get the same family
        Map<Statistic, MeasurementFamily> familiesByStatistic = new EnumMap<>(Statistic.class);
        for (Measurement measurement : measurements) {
            familiesByStatistic.computeIfAbsent(measurement.getStatistic(), statistic -> {
                List<String> statValues = new ArrayList<>(context.tagValues);
                statValues.add(statistic.toString());
                return new MeasurementFamily(customMeterFamily(statistic, statKeys, context.help),
                        Labels.of(statKeys, statValues));
            });
        }

        add(context.id, samples -> {
            for (Measurement measurement : measurements) {
                MeasurementFamily family = requireNonNull(familiesByStatistic.get(measurement.getStatistic()));
                if (family.descriptor.getType() == MetricType.COUNTER) {
                    samples.accept(family.descriptor, new CounterDataPointSnapshot(measurement.getValue(),
                            family.labels, null, context.createdTimestampMillis));
                }
                else {
                    samples.accept(family.descriptor,
                            new GaugeDataPointSnapshot(measurement.getValue(), family.labels, null));
                }
            }
        });
    }

    private MetricFamilyDescriptor customMeterFamily(Statistic statistic, Collection<String> labelNames, String help) {
        switch (statistic) {
            case TOTAL:
            case TOTAL_TIME:
                return familyDescriptor(MetricType.COUNTER, conventionName + "_sum", labelNames, help);
            case COUNT:
                return familyDescriptor(MetricType.COUNTER, conventionName, labelNames, help);
            case MAX:
                return familyDescriptor(MetricType.GAUGE, conventionName + "_max", labelNames, help);
            case VALUE:
            case UNKNOWN:
                return familyDescriptor(MetricType.GAUGE, conventionName + "_value", labelNames, help);
            case ACTIVE_TASKS:
                return familyDescriptor(MetricType.GAUGE, conventionName + "_active_count", labelNames, help);
            case DURATION:
                return familyDescriptor(MetricType.GAUGE, conventionName + "_duration_sum", labelNames, help);
            default:
                throw new IllegalArgumentException("Unsupported meter statistic: " + statistic);
        }
    }

    private void addSingleDataPoint(MeterContext context, MetricFamilyDescriptor family,
            Supplier<DataPointSnapshot> dataPoint) {
        add(context.id, samples -> samples.accept(family, dataPoint.get()));
    }

    void add(Meter.Id id, Child child) {
        children.put(id, child);
    }

    // Descriptors are intentionally not removed with their meter: a family descriptor is
    // valid for every meter of the family, and a collector with no meters left is
    // unregistered and discarded as a whole.
    void remove(Meter.Id id) {
        children.remove(id);
    }

    boolean isEmpty() {
        return children.isEmpty();
    }

    Meter.Id getOriginalId() {
        return originalMeterId;
    }

    /**
     * Returns the descriptor of the given metric family, creating it if this is the first
     * meter contributing to the family. All meters of a family share one descriptor since
     * it is the same for each of them.
     * <p>
     * Fails instead of failing later on scrape if the family was already registered with
     * a different type, e.g. when only some meters with the same name publish histogram
     * buckets. {@link io.prometheus.metrics.model.registry.PrometheusRegistry} only
     * validates this for the families present when the collector is registered, not for
     * meters added to this collector afterwards.
     */
    MetricFamilyDescriptor getOrCreateDescriptor(MetricType metricType, String familyName,
            Supplier<MetricFamilyDescriptor> factory) {
        MetricFamilyDescriptor descriptor = descriptorsByFamilyName.computeIfAbsent(familyName, name -> factory.get());
        if (descriptor.getType() != metricType) {
            throw new IllegalArgumentException(
                    "Meters with the same name must produce the same Prometheus metric family types. The family ("
                            + familyName + ") was already registered with type " + descriptor.getType()
                            + ", but this meter produces type " + metricType + ". This can happen when only some"
                            + " meters with the same name publish histogram buckets.");
        }
        return descriptor;
    }

    private MetricFamilyDescriptor familyDescriptor(MetricType metricType, String familyName,
            Collection<String> labelNames, String help) {
        // Unit is intentionally not set, see:
        // https://github.com/OpenObservability/OpenMetrics/blob/1386544931307dff279688f332890c31b6c5de36/specification/OpenMetrics.md#unit
        return getOrCreateDescriptor(metricType, familyName,
                () -> MetricFamilyDescriptor.of(metricType, familyName).help(help).labelNames(labelNames).build());
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return new ArrayList<>(descriptorsByFamilyName.values());
    }

    @Override
    public MetricSnapshots collect() {
        // descriptors are canonical per family, so grouping by descriptor groups by
        // family
        Map<MetricFamilyDescriptor, List<DataPointSnapshot>> dataPointsByFamily = new IdentityHashMap<>();
        BiConsumer<MetricFamilyDescriptor, DataPointSnapshot> sink = (family,
                dataPoint) -> dataPointsByFamily.computeIfAbsent(family, f -> new ArrayList<>()).add(dataPoint);

        for (Child child : children.values()) {
            child.collect(sink);
        }

        List<MetricSnapshot> snapshots = new ArrayList<>(dataPointsByFamily.size());
        dataPointsByFamily.forEach((family, dataPoints) -> snapshots.add(createSnapshot(family, dataPoints)));
        return new MetricSnapshots(snapshots);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private MetricSnapshot createSnapshot(MetricFamilyDescriptor descriptor, List<DataPointSnapshot> dataPoints) {
        MetricMetadata metadata = descriptor.getMetadata();
        switch (descriptor.getType()) {
            case COUNTER:
                return new CounterSnapshot(metadata, (List) dataPoints);
            case GAUGE:
                return new GaugeSnapshot(metadata, (List) dataPoints);
            case SUMMARY:
                return new SummarySnapshot(metadata, (List) dataPoints);
            case HISTOGRAM:
                // A LongTaskTimer histogram is point-in-time and non-monotonic, which
                // maps to a Prometheus gauge histogram; it is the only meter for which
                // we produce one.
                return new HistogramSnapshot(originalMeterId.getType() == Meter.Type.LONG_TASK_TIMER, metadata,
                        (List) dataPoints);
            case INFO:
                return new InfoSnapshot(metadata, (List) dataPoints);
            default:
                throw new IllegalArgumentException("Unsupported metric type: " + descriptor.getType());
        }
    }

    private static Quantiles quantiles(ValueAtPercentile[] percentileValues, @Nullable TimeUnit timeUnit) {
        if (percentileValues.length == 0) {
            return Quantiles.EMPTY;
        }
        List<Quantile> quantiles = new ArrayList<>(percentileValues.length);
        for (ValueAtPercentile percentileValue : percentileValues) {
            quantiles.add(new Quantile(percentileValue.percentile(),
                    timeUnit == null ? percentileValue.value() : percentileValue.value(timeUnit)));
        }
        return Quantiles.of(quantiles);
    }

    // TODO: remove this cumulative -> non cumulative conversion
    // ClassicHistogramBuckets is not cumulative but the histograms we use are cumulative
    // so we convert it to non-cumulative just for the Prometheus client library
    // can convert it back to cumulative.
    private static ClassicHistogramBuckets nonCumulativeBuckets(CountAtBucket[] histogramCounts, long count,
            @Nullable TimeUnit timeUnit) {
        List<Double> buckets = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        buckets.add(bucket(histogramCounts[0], timeUnit));
        counts.add(histogramCounts[0].count());
        for (int i = 1; i < histogramCounts.length; i++) {
            buckets.add(bucket(histogramCounts[i], timeUnit));
            counts.add(histogramCounts[i].count() - histogramCounts[i - 1].count());
        }
        if (Double.isFinite(histogramCounts[histogramCounts.length - 1].bucket())) {
            // ClassicHistogramBuckets is not cumulative
            buckets.add(Double.POSITIVE_INFINITY);
            double infCount = count - histogramCounts[histogramCounts.length - 1].count();
            counts.add(infCount >= 0 ? infCount : 0);
        }
        return ClassicHistogramBuckets.of(buckets, counts);
    }

    private static double bucket(CountAtBucket countAtBucket, @Nullable TimeUnit timeUnit) {
        return timeUnit == null ? countAtBucket.bucket() : countAtBucket.bucket(timeUnit);
    }

    private static Exemplars exemplarsInSeconds(Exemplars exemplars) {
        List<Exemplar> scaled = new ArrayList<>(exemplars.size());
        for (Exemplar exemplar : exemplars) {
            scaled.add(Exemplar.builder()
                .value(TimeUtils.convert(exemplar.getValue(), NANOSECONDS, SECONDS))
                .labels(exemplar.getLabels())
                .timestampMillis(exemplar.getTimestampMillis())
                .build());
        }
        return Exemplars.of(scaled);
    }

    @FunctionalInterface
    interface Child {

        void collect(BiConsumer<MetricFamilyDescriptor, DataPointSnapshot> samples);

    }

    /**
     * The metric family and precomputed labels for the measurements of one
     * {@link Statistic} of a custom meter.
     */
    private static final class MeasurementFamily {

        private final MetricFamilyDescriptor descriptor;

        private final Labels labels;

        private MeasurementFamily(MetricFamilyDescriptor descriptor, Labels labels) {
            this.descriptor = descriptor;
            this.labels = labels;
        }

    }

}
