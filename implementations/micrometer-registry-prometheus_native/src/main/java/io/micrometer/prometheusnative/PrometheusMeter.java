/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.prometheusnative;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;

import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class PrometheusMeter<T extends Collector, S extends DataPointSnapshot> implements Meter {

    private final Meter.Id id;

    private final T collector;

    private final Labels labels;

    /**
     * Collect a single data point.
     * <p>
     * This first collects the entire metric, and then returns the data point where the
     * labels match {@link Id#getTags()}.
     */
    protected S collect() {
        MetricSnapshot snapshot = collector.collect();
        for (DataPointSnapshot dataPoint : snapshot.getData()) {
            if (labels.equals(dataPoint.getLabels())) {
                return (S) dataPoint;
            }
        }
        throw new IllegalStateException(
                "No Prometheus labels found for Micrometer's tags. This is a bug in the Prometheus meter registry.");
    }

    PrometheusMeter(Meter.Id id, T collector) {
        this.id = id;
        this.collector = collector;
        this.labels = makeLabels(id.getTags());
    }

    private Labels makeLabels(List<Tag> tags) {
        Labels.Builder builder = Labels.newBuilder();
        for (Tag tag : tags) {
            builder.addLabel(PrometheusNaming.sanitizeLabelName(tag.getKey()), tag.getValue());
        }
        return builder.build();
    }

    @Override
    public Id getId() {
        return id;
    }

    protected double toUnit(double seconds, TimeUnit unit) {
        return seconds * TimeUnit.SECONDS.toNanos(1) / (double) unit.toNanos(1);
    }

}
