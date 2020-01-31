package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    private static final String METRIC_NAME_PREFIX = "kafka.";

    private final Supplier<Map<MetricName, ? extends Metric>> metricsSupplier;

    private final Iterable<Tag> extraTags;

    /**
     * Keep track of current number of metrics. When this value changes, metrics are re-bind.
     */
    private AtomicInteger currentSize = new AtomicInteger(0);

    /**
     * Kafka Producer metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags) {
        this(kafkaProducer::metrics, tags);
    }

    /**
     * Kafka Producer metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     */
    public KafkaMetrics(Producer<?, ?> kafkaProducer) {
        this(kafkaProducer::metrics);
    }

    /**
     * Kafka Consumer metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags) {
        this(kafkaConsumer::metrics, tags);
    }

    /**
     * Kafka Consumer metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     */
    public KafkaMetrics(Consumer<?, ?> kafkaConsumer) {
        this(kafkaConsumer::metrics);
    }

    /**
     * Kafka Streams metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     * @param tags         additional tags
     */
    public KafkaMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags) {
        this(kafkaStreams::metrics, tags);
    }

    /**
     * Kafka Streams metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     */
    public KafkaMetrics(KafkaStreams kafkaStreams) {
        this(kafkaStreams::metrics);
    }

    /**
     * Kafka Admin Client metrics binder
     *
     * @param adminClient instance to be instrumented
     * @param tags        additional tags
     */
    public KafkaMetrics(AdminClient adminClient, Iterable<Tag> tags) {
        this(adminClient::metrics, tags);
    }

    /**
     * Kafka Admin client metrics binder
     *
     * @param adminClient instance to be instrumented
     */
    public KafkaMetrics(AdminClient adminClient) {
        this(adminClient::metrics);
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = emptyList();
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier,
                 Iterable<Tag> extraTags) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = extraTags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        checkAndRegisterMetrics(registry);
    }

    private void checkAndRegisterMetrics(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        if (currentSize.get() != metrics.size()) { // only triggered when number of metrics change
            currentSize.set(metrics.size());
            Map<String, Set<Meter>> registered = new HashMap<>();
            //TODO filter out the following metrics: count (num of metrics), app.info metadata
            // @jeqo: should we add app.info fields as tags? e.g. version and commit.id could be useful
            metrics.forEach((name, metric) -> {
                // register metric
                String metricName = metricName(metric);
                Meter meter;
                if (metricName.endsWith("total")
                        || metricName.endsWith("count")) {
                    meter = registerCounter(registry, metric, metricName, extraTags);
                } else if (metricName.endsWith("min")
                        || metricName.endsWith("max")
                        || metricName.endsWith("avg")) {
                    meter = registerGauge(registry, metric, metricName, extraTags);
                } else if (metricName.endsWith("rate")) {
                    meter = registerTimeGauge(registry, metric, metricName, extraTags);
                } else { // this filter might need to be more extensive.
                    meter = registerGauge(registry, metric, metricName, extraTags);
                }
                // collect metrics with same name to validate number of labels
                Set<Meter> meters = registered.get(metricName);
                if (meters == null) meters = new HashSet<>();
                meters.add(meter);
                registered.put(metricName, meters);
            });

            // clean up metrics with low number of tags
            registered.forEach((metricName, meters) -> {
                if (meters.size() > 1) {
                    // find largest number of tags
                    int maxTagsSize = 0;
                    for (Meter meter : meters) {
                        int size = meter.getId().getTags().size();
                        if (maxTagsSize < size) maxTagsSize = size;
                    }
                    // remove meters with lower number of tags
                    for (Meter meter : meters) {
                        if (meter.getId().getTags().size() < maxTagsSize) registry.remove(meter);
                    }
                }
            });
        }
    }

    private TimeGauge registerTimeGauge(MeterRegistry registry, Metric metric, String metricName,
                                        Iterable<Tag> extraTags) {
        return TimeGauge.builder(
                metricName, metric, TimeUnit.SECONDS, m -> {
                    checkAndRegisterMetrics(registry);
                    if (m.metricValue() instanceof Double) {
                        return (double) m.metricValue();
                    } else {
                        return Double.NaN;
                    }
                })
                .tags(metric.metricName().tags()
                        .entrySet()
                        .stream()
                        .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private Gauge registerGauge(
        MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        return Gauge.builder(
                metricName, metric, m -> {
                    checkAndRegisterMetrics(registry);
                    if (m.metricValue() instanceof Double) {
                        return (double) m.metricValue();
                    } else {
                        return Double.NaN;
                    }
                })
                .tags(metric.metricName().tags()
                        .entrySet()
                        .stream()
                        .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private FunctionCounter registerCounter(MeterRegistry registry, Metric metric, String metricName,
                                            Iterable<Tag> extraTags) {
        return FunctionCounter.builder(
                metricName, metric, m -> {
                    checkAndRegisterMetrics(registry);
                    if (m.metricValue() instanceof Double) {
                        return (double) m.metricValue();
                    } else {
                        return Double.NaN;
                    }
                })
                .tags(metric.metricName().tags()
                        .entrySet()
                        .stream()
                        .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))
                .tags(extraTags)
                .description(metric.metricName().description())
                .register(registry);
    }

    private String metricName(Metric metric) {
        String value =
                METRIC_NAME_PREFIX + metric.metricName().group() + "." + metric.metricName().name();
        return value.replaceAll("-", ".");
    }
}
