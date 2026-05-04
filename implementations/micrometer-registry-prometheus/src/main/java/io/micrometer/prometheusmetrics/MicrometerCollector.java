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

import io.micrometer.core.instrument.Meter;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.MetricType;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * {@link MultiCollector} for Micrometer.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
class MicrometerCollector implements MultiCollector {

    private final Map<Meter.Id, Child> children = new ConcurrentHashMap<>();

    private final String conventionName;

    // the id of the meter used to create this MicrometerCollector
    private final Meter.Id originalMeterId;

    // take name to avoid calling NamingConvention#name after the call-site has already
    // done it
    MicrometerCollector(String name, Meter.Id id) {
        this.conventionName = name;
        this.originalMeterId = id;
    }

    public void add(Meter.Id id, Child child) {
        children.put(id, child);
    }

    public void remove(Meter.Id id) {
        children.remove(id);
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    Meter.Type getMeterType() {
        return originalMeterId.getType();
    }

    Meter.Id getOriginalId() {
        return originalMeterId;
    }

    @Override
    public List<String> getPrometheusNames() {
        return metricSnapshots().stream()
            .map(snapshot -> snapshot.getMetadata().getPrometheusName())
            .distinct()
            .collect(toList());
    }

    @Override
    public @Nullable MetricType getMetricType(String prometheusName) {
        for (MetricSnapshot snapshot : metricSnapshots()) {
            if (snapshot.getMetadata().getPrometheusName().equals(prometheusName)) {
                if (snapshot instanceof CounterSnapshot) {
                    return MetricType.COUNTER;
                }
                if (snapshot instanceof InfoSnapshot) {
                    return MetricType.INFO;
                }
                if (snapshot instanceof HistogramSnapshot) {
                    return MetricType.HISTOGRAM;
                }
                if (snapshot instanceof io.prometheus.metrics.model.snapshots.SummarySnapshot) {
                    return MetricType.SUMMARY;
                }
                if (snapshot instanceof GaugeSnapshot) {
                    return MetricType.GAUGE;
                }
                return MetricType.UNKNOWN;
            }
        }
        return null;
    }

    @Override
    public @Nullable Set<String> getLabelNames(String prometheusName) {
        Set<String> names = new HashSet<>();
        for (MetricSnapshot snapshot : metricSnapshots()) {
            if (snapshot.getMetadata().getPrometheusName().equals(prometheusName)) {
                for (DataPointSnapshot dataPoint : snapshot.getDataPoints()) {
                    dataPoint.getLabels().forEach(label -> names.add(label.getName()));
                }
            }
        }
        return names.isEmpty() ? null : names;
    }

    @Override
    public @Nullable MetricMetadata getMetadata(String prometheusName) {
        for (MetricSnapshot snapshot : metricSnapshots()) {
            if (snapshot.getMetadata().getPrometheusName().equals(prometheusName)) {
                return snapshot.getMetadata();
            }
        }
        return null;
    }

    @Override
    public MetricSnapshots collect() {
        Map<String, Family> families = new HashMap<>();

        for (Child child : children.values()) {
            child.samples(conventionName)
                .forEach(family -> families.compute(family.getConventionName(),
                        (name, matchingFamily) -> matchingFamily != null
                                ? matchingFamily.addSamples(family.dataPointSnapshots) : family));
        }

        Collection<MetricSnapshot> metricSnapshots = families.values()
            .stream()
            .map(Family::toMetricSnapshot)
            .collect(toList());

        return new MetricSnapshots(metricSnapshots);
    }

    private List<MetricSnapshot> metricSnapshots() {
        return collect().stream().collect(toList());
    }

    interface Child {

        Stream<Family<?>> samples(String conventionName);

    }

    static class Family<T extends DataPointSnapshot> {

        final String conventionName;

        final MetricMetadata metadata;

        final List<T> dataPointSnapshots = new ArrayList<>();

        final Function<Family<T>, MetricSnapshot> metricSnapshotFactory;

        Family(String conventionName, Function<Family<T>, MetricSnapshot> metricSnapshotFactory,
                MetricMetadata metadata, T... dataPointSnapshots) {
            this.conventionName = conventionName;
            this.metricSnapshotFactory = metricSnapshotFactory;
            this.metadata = metadata;
            Collections.addAll(this.dataPointSnapshots, dataPointSnapshots);
        }

        String getConventionName() {
            return conventionName;
        }

        Family<T> addSamples(Collection<T> dataPointSnapshots) {
            this.dataPointSnapshots.addAll(dataPointSnapshots);
            return this;
        }

        MetricSnapshot toMetricSnapshot() {
            return metricSnapshotFactory.apply(this);
        }

    }

}
