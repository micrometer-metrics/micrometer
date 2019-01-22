/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * @author Oleksii Bondar
 */
public abstract class AbstractKafkaMetrics implements MetricsReporter {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String METRIC_GROUP_TAG = "metric.group";
    
    public abstract String getMetricPrefix();

    public void configure(Map<String, ?> configs) {
        // nothing needed
    }

    public void metricRemoval(KafkaMetric metric) {
        // nothing needed
    }

    public void close() {
        // nothing needed
    }

    public void init(List<KafkaMetric> metrics) {
        // nothing needed
    }

    public void metricChange(KafkaMetric metric) {
        if (metric.metricValue() instanceof Double) {
            MetricName metricNameRef = metric.metricName();
            String groupName = metricNameRef.group();
            String metricName = prepareName(metricNameRef.name());
            Map<String, String> metricTags = metricNameRef.tags();
            Set<Tag> tags = metricTags.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).collect(toSet());
            tags.add(Tag.of(METRIC_GROUP_TAG, groupName));
            Metrics.gauge(metricName, tags, metric, m -> (Double) m.metricValue());
            logger.debug("Registering metric [{}]", metricName);
        }
    }

    /**
     * Prepares uniformed metric name to ease migration from {@link KafkaConsumerMetrics}
     */
    private String prepareName(String name) {
        return (getMetricPrefix() + name).replaceAll("-", ".");
    }

}
