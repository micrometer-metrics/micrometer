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
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<Meter.Id, List<MetricFamilyDescriptor>> registeredFamilies = new ConcurrentHashMap<>();

    private final Map<MetricFamilyDescriptor, SnapshotFactory> customSnapshotFactories = new ConcurrentHashMap<>();

    final String conventionName;

    // the id of the meter used to create this MicrometerCollector
    private final Meter.Id originalMeterId;

    // take name to avoid calling NamingConvention#name after the call-site has already
    // done it
    MicrometerCollector(String name, Meter.Id id) {
        this.conventionName = name;
        this.originalMeterId = id;
    }

    public void add(Meter.Id id, Child child, MetricFamilyDescriptor... registeredFamilies) {
        children.put(id, child);
        this.registeredFamilies.put(id, Arrays.asList(registeredFamilies));
    }

    public void registerCustomSnapshotFactory(MetricFamilyDescriptor descriptor, SnapshotFactory factory) {
        customSnapshotFactories.put(descriptor, factory);
    }

    public void remove(Meter.Id id) {
        children.remove(id);
        List<MetricFamilyDescriptor> descriptors = registeredFamilies.remove(id);
        if (descriptors != null) {
            descriptors.forEach(customSnapshotFactories::remove);
        }
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
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return registeredFamilies.values().stream().flatMap(Collection::stream).collect(toList());
    }

    @Override
    public MetricSnapshots collect() {
        Map<MetricFamilyDescriptor, List<DataPointSnapshot>> familyDataPoints = new LinkedHashMap<>();

        for (Child child : children.values()) {
            child.collect(familyDataPoints);
        }

        List<MetricSnapshot> snapshots = new ArrayList<>(familyDataPoints.size());
        for (Map.Entry<MetricFamilyDescriptor, List<DataPointSnapshot>> entry : familyDataPoints.entrySet()) {
            MetricFamilyDescriptor descriptor = entry.getKey();
            List<DataPointSnapshot> dataPoints = entry.getValue();
            if (!dataPoints.isEmpty()) {
                snapshots.add(createSnapshot(descriptor, dataPoints));
            }
        }

        return new MetricSnapshots(snapshots);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private MetricSnapshot createSnapshot(MetricFamilyDescriptor descriptor, List<DataPointSnapshot> dataPoints) {
        SnapshotFactory customFactory = customSnapshotFactories.get(descriptor);
        if (customFactory != null) {
            return customFactory.create(descriptor.getMetadata(), (List) dataPoints);
        }
        MetricMetadata metadata = descriptor.getMetadata();
        switch (descriptor.getType()) {
            case COUNTER:
                return new CounterSnapshot(metadata, (List) dataPoints);
            case GAUGE:
                return new GaugeSnapshot(metadata, (List) dataPoints);
            case SUMMARY:
                return new SummarySnapshot(metadata, (List) dataPoints);
            case HISTOGRAM:
                return new HistogramSnapshot(metadata, (List) dataPoints);
            case INFO:
                return new InfoSnapshot(metadata, (List) dataPoints);
            default:
                throw new IllegalArgumentException("Unsupported metric type: " + descriptor.getType());
        }
    }

    @FunctionalInterface
    interface Child {

        void collect(Map<MetricFamilyDescriptor, List<DataPointSnapshot>> samples);

    }

    @FunctionalInterface
    interface SnapshotFactory {

        MetricSnapshot create(MetricMetadata metadata, List<DataPointSnapshot> dataPoints);

    }

}
