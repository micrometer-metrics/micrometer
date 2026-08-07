/*
 * Copyright 2026 VMware, Inc.
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
import io.prometheus.metrics.model.snapshots.Labels;

import java.util.List;

/**
 * Everything a {@link MicrometerCollector} needs about a meter that must be derived from
 * the registry when the meter is created rather than when it is scraped: the naming
 * convention is applied to the tags, the configured help text is resolved, and the
 * created timestamp is taken from the registry clock. Doing this once per meter keeps the
 * {@link io.micrometer.core.instrument.config.NamingConvention} off the scrape path.
 *
 * @author Tommy Ludwig
 */
final class MeterContext {

    final Meter.Id id;

    /**
     * Convention tag keys, used as the label names of the metric families.
     */
    final List<String> tagKeys;

    /**
     * Tag values in the same order as {@link #tagKeys}.
     */
    final List<String> tagValues;

    /**
     * The labels of the data points, i.e. {@link #tagKeys} zipped with
     * {@link #tagValues}. Meters that add labels of their own, such as custom meters,
     * build their labels from the keys and values instead.
     */
    final Labels labels;

    final String help;

    final long createdTimestampMillis;

    MeterContext(Meter.Id id, List<String> tagKeys, List<String> tagValues, String help, long createdTimestampMillis) {
        this.id = id;
        this.tagKeys = tagKeys;
        this.tagValues = tagValues;
        this.labels = Labels.of(tagKeys, tagValues);
        this.help = help;
        this.createdTimestampMillis = createdTimestampMillis;
    }

}
