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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.toList;

/**
 * {@link MultiCollector} for Micrometer.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
class MicrometerCollector implements MultiCollector {

    private final Map<Meter.Id, Registration> registrations = new ConcurrentHashMap<>();

    final String conventionName;

    // the id of the meter used to create this MicrometerCollector
    private final Meter.Id originalMeterId;

    // take name to avoid calling NamingConvention#name after the call-site has already
    // done it
    MicrometerCollector(String name, Meter.Id id) {
        this.conventionName = name;
        this.originalMeterId = id;
    }

    public void add(Meter.Id id, Child child, MetricFamilyDescriptor... familyDescriptors) {
        validateTypesMatchExistingFamilies(familyDescriptors);
        registrations.put(id, new Registration(child, Arrays.asList(familyDescriptors)));
    }

    /**
     * Fail registration instead of failing later on scrape if the given family
     * descriptors conflict in type with the metric families of the already registered
     * meters, e.g. only some meters with the same name publish histogram buckets.
     * {@link io.prometheus.metrics.model.registry.PrometheusRegistry} only validates this
     * for the families present when the collector is registered, not for meters added to
     * this collector afterwards.
     */
    private void validateTypesMatchExistingFamilies(MetricFamilyDescriptor[] familyDescriptors) {
        // Existing registrations are mutually consistent since each was validated when
        // added, so validating against a single one of them is sufficient; only the
        // primary family of distribution meters can vary in type (SUMMARY vs HISTOGRAM)
        // and every distribution meter registers it.
        Iterator<Registration> registrationIterator = registrations.values().iterator();
        if (!registrationIterator.hasNext()) {
            return;
        }
        List<MetricFamilyDescriptor> existingDescriptors = registrationIterator.next().familyDescriptors;
        for (MetricFamilyDescriptor descriptor : familyDescriptors) {
            for (MetricFamilyDescriptor existingDescriptor : existingDescriptors) {
                if (descriptor.getPrometheusName().equals(existingDescriptor.getPrometheusName())
                        && descriptor.getType() != existingDescriptor.getType()) {
                    throw new IllegalArgumentException(
                            "Meters with the same name must produce the same Prometheus metric family types. The family ("
                                    + descriptor.getPrometheusName() + ") was already registered with type "
                                    + existingDescriptor.getType() + ", but this meter produces type "
                                    + descriptor.getType() + ". This can happen when only some meters with"
                                    + " the same name publish histogram buckets.");
                }
            }
        }
    }

    public void remove(Meter.Id id) {
        registrations.remove(id);
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    Meter.Id getOriginalId() {
        return originalMeterId;
    }

    @Override
    public List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return registrations.values()
            .stream()
            .flatMap(registration -> registration.familyDescriptors.stream())
            .collect(toList());
    }

    @Override
    public MetricSnapshots collect() {
        // group data points from all children by family name so that each family is
        // exposed as a single MetricSnapshot
        Map<String, FamilySamples> samplesByFamilyName = new LinkedHashMap<>();
        BiConsumer<MetricFamilyDescriptor, DataPointSnapshot> sink = (descriptor, dataPoint) -> samplesByFamilyName
            .computeIfAbsent(descriptor.getPrometheusName(), name -> new FamilySamples(descriptor)).dataPoints
            .add(dataPoint);

        for (Registration registration : registrations.values()) {
            registration.child.collect(sink);
        }

        List<MetricSnapshot> snapshots = new ArrayList<>(samplesByFamilyName.size());
        for (FamilySamples familySamples : samplesByFamilyName.values()) {
            snapshots.add(createSnapshot(familySamples.descriptor, familySamples.dataPoints));
        }

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

    @FunctionalInterface
    interface Child {

        void collect(BiConsumer<MetricFamilyDescriptor, DataPointSnapshot> samples);

    }

    /**
     * A {@link Child} together with the family descriptors it was registered with.
     */
    private static final class Registration {

        private final Child child;

        private final List<MetricFamilyDescriptor> familyDescriptors;

        private Registration(Child child, List<MetricFamilyDescriptor> familyDescriptors) {
            this.child = child;
            this.familyDescriptors = familyDescriptors;
        }

    }

    /**
     * Data points collected for one metric family during a single {@link #collect()}
     * call. Since children register their own descriptor instances, the descriptor of the
     * first child that contributes to the family is used for the snapshot metadata.
     */
    private static final class FamilySamples {

        private final MetricFamilyDescriptor descriptor;

        private final List<DataPointSnapshot> dataPoints = new ArrayList<>();

        private FamilySamples(MetricFamilyDescriptor descriptor) {
            this.descriptor = descriptor;
        }

    }

}
