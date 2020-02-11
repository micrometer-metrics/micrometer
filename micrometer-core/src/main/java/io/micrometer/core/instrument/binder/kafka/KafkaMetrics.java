/**
 * Copyright 2020 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.streams.KafkaStreams;

import static java.util.Collections.emptyList;

/**
 * Kafka metrics binder.
 * <p>
 * It is based on {@code metrics()} method returning {@link Metric} map exposed by clients and
 * streams interface.
 * <p>
 * Meter names have the following convention: {@code kafka.(metric_group).(metric_name)}
 *
 * @author Jorge Quilcate
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring
 * documentation</a>
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
@NonNullApi
@NonNullFields
public class KafkaMetrics implements MeterBinder {
    static final String METRIC_NAME_PREFIX = "kafka.";

    static final String METRIC_GROUP_APP_INFO = "app-info";
    static final String VERSION_METRIC_NAME = "version";
    static final String START_TIME_METRIC_NAME = "start-time-ms";

    private final Supplier<Map<MetricName, ? extends Metric>> metricsSupplier;
    private final Iterable<Tag> extraTags;

    /**
     * Keeps track of current set of metrics. When this values change, metrics are bound again.
     */
    private volatile Map<MetricName, Meter> currentMeters = new HashMap<>();

    private String version = "";

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags) {
        this(kafkaProducer::metrics, tags);
    }

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     */
    public KafkaMetrics(Producer<?, ?> kafkaProducer) {
        this(kafkaProducer::metrics);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags) {
        this(kafkaConsumer::metrics, tags);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     */
    public KafkaMetrics(Consumer<?, ?> kafkaConsumer) {
        this(kafkaConsumer::metrics);
    }

    /**
     * {@link KafkaStreams} metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     * @param tags         additional tags
     */
    public KafkaMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags) {
        this(kafkaStreams::metrics, tags);
    }

    /**
     * {@link KafkaStreams} metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     */
    public KafkaMetrics(KafkaStreams kafkaStreams) {
        this(kafkaStreams::metrics);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     * @param tags        additional tags
     */
    public KafkaMetrics(AdminClient adminClient, Iterable<Tag> tags) {
        this(adminClient::metrics, tags);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     */
    public KafkaMetrics(AdminClient adminClient) {
        this(adminClient::metrics);
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier) {
        this(metricsSupplier, emptyList());
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier,
            Iterable<Tag> extraTags) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = extraTags;
    }

    @Override public void bindTo(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        metrics.forEach((name, metric) -> {
            //Filter out metrics from group "app-info", that includes metadata
            if (METRIC_GROUP_APP_INFO.equals(name.group())) {
                if (VERSION_METRIC_NAME.equals(name.name())) version = (String) metric.metricValue();
                else if (START_TIME_METRIC_NAME.equals(name.name())) bindMeter(registry, metric);
            }
        });
        checkAndBindMetrics(registry);
    }

    /**
     * Gather metrics from Kafka metrics API and register Meters.
     * <p>
     * As this is a one-off execution when binding a Kafka client, Meters include a call to this
     * validation to double-check new metrics when returning values. This should only add the cost of
     * validating meters registered counter when no new meters are present.
     */
    void checkAndBindMetrics(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        if (!currentMeters.keySet().equals(metrics.keySet())) {
            synchronized (this) { //Enforce only happens once when metrics change
                if (!currentMeters.keySet().equals(metrics.keySet())) {
                    //Clean registry
                    for (Meter meter : currentMeters.values()) registry.remove(meter);
                    //Register meters
                    currentMeters = new HashMap<>();
                    metrics.forEach((name, metric) -> {
                        //Filter out metrics from group "app-info", that includes metadata
                        if (METRIC_GROUP_APP_INFO.equals(name.group())) return;
                        currentMeters.put(name, bindMeter(registry, metric));
                    });
                }
            }
        }
    }

    @NonNull private Meter bindMeter(MeterRegistry registry, Metric metric) {
        String name = metricName(metric);
        if (name.endsWith("total") || name.endsWith("count"))
            return registerCounter(registry, metric, name, extraTags);
        else if (name.endsWith("min") || name.endsWith("max") || name.endsWith("avg"))
            return registerGauge(registry, metric, name, extraTags);
        else if (name.endsWith("rate"))
            return registerTimeGauge(registry, metric, name, extraTags);
        else return registerGauge(registry, metric, name, extraTags);
    }

    private TimeGauge registerTimeGauge(MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        return TimeGauge.builder(metricName, metric, TimeUnit.SECONDS, toMetricValue(registry))
                .tags(metricTags(metric))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private Gauge registerGauge(MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        return Gauge.builder(metricName, metric, toMetricValue(registry))
                .tags(metricTags(metric))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private FunctionCounter registerCounter(MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        return FunctionCounter.builder(metricName, metric, toMetricValue(registry))
                .tags(metricTags(metric))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private ToDoubleFunction<Metric> toMetricValue(MeterRegistry registry) {
        return metric -> {
            //Double-check if new metrics are registered; if not (common scenario)
            //it only adds metrics count validation
            checkAndBindMetrics(registry);
            if (metric.metricValue() instanceof Double) return (double) metric.metricValue();
            else if (metric.metricValue() instanceof Integer) return ((Integer) metric.metricValue()).doubleValue();
            else if (metric.metricValue() instanceof Long) return ((Long) metric.metricValue()).doubleValue();
            else return 0.0;
        };
    }

    private List<Tag> metricTags(Metric metric) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("version", version));
        for (Map.Entry<String, String> entry: metric.metricName().tags().entrySet()) {
            tags.add(Tag.of(entry.getKey(), entry.getValue()));
        }
        return tags;
    }

    private String metricName(Metric metric) {
        String name = METRIC_NAME_PREFIX + metric.metricName().group() + "." + metric.metricName().name();
        return name.replaceAll("-", ".");
    }
}
