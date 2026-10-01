/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.MultiGauge.Row;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.streams.KafkaStreams;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Kafka Streams metrics binder.
 * <p>
 * It is based on the Kafka client's {@code metrics()} method returning a {@link Metric}
 * map.
 * <p>
 * Meter names have the following convention: {@code kafka.(metric_group).(metric_name)}
 * <p>
 * Note: the {@link #close()} method should be called when the application shuts down to
 * shut down the internal metrics-refresh scheduler (when not externally managed) and to
 * remove the meters this binder registered.
 *
 * @author Jorge Quilcate
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring
 * documentation</a>
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
public class KafkaStreamsMetrics extends KafkaMetrics {

    private static final String STREAM_METRICS_GROUP = "stream-metrics";

    private static final String STATE_METRIC_NAME = "state";

    private final KafkaStreams kafkaStreams;

    private @Nullable MultiGauge stateGauge;

    /**
     * {@link KafkaStreams} metrics binder
     * @param kafkaStreams instance to be instrumented
     * @param tags additional tags
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags) {
        super(kafkaStreams::metrics, tags);
        this.kafkaStreams = kafkaStreams;
    }

    /**
     * {@link KafkaStreams} metrics binder
     * @param kafkaStreams instance to be instrumented
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams) {
        super(kafkaStreams::metrics);
        this.kafkaStreams = kafkaStreams;
    }

    /**
     * {@link KafkaStreams} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaStreamsMetrics} to reduce resource usage by reducing the number of
     * threads if there will be many instances.
     * @param kafkaStreams instance to be instrumented
     * @param tags additional tags
     * @param scheduler customer scheduler to run the task that checks and binds metrics
     * @since 1.14.0
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags, ScheduledExecutorService scheduler) {
        super(kafkaStreams::metrics, tags, scheduler);
        this.kafkaStreams = kafkaStreams;
    }

    /**
     * {@link KafkaStreams} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaStreamsMetrics} to reduce resource usage by reducing the number of
     * threads if there will be many instances.
     * <p>
     * The refresh interval governs how frequently Micrometer should call the Kafka
     * Client's Metrics API to discover new metrics to register and discard old ones since
     * the Kafka Client can add/remove/recreate metrics on-the-fly. Please notice that
     * this is not for fetching values for already registered metrics but for updating the
     * list of registered metrics when the Kafka Client adds/removes/recreates them. It is
     * the responsibility of the caller to choose the right value since this process can
     * be expensive and metrics can appear and disappear without being published if the
     * interval is not chosen appropriately.
     * @param kafkaStreams instance to be instrumented
     * @param tags additional tags
     * @param scheduler customer scheduler to run the task that checks and binds metrics
     * @param refreshInterval interval of discovering new/removed/recreated metrics by the
     * Kafka Client
     * @since 1.16.0
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags, ScheduledExecutorService scheduler,
            Duration refreshInterval) {
        super(kafkaStreams::metrics, tags, scheduler, refreshInterval);
        this.kafkaStreams = kafkaStreams;
    }

    /**
     * Registers a gauge reporting the current {@link KafkaStreams.State application
     * state}.
     * <p>
     * The Kafka client reports the state as an enum, which Micrometer cannot publish as a
     * numeric value, so it is normally filtered out. Following the <a href=
     * "https://github.com/prometheus/OpenMetrics/blob/main/specification/OpenMetrics.md#stateset-1">OpenMetrics
     * StateSet</a> pattern, this registers one time series per possible state, tagged
     * with {@code state}; the current state has value {@code 1} and all others {@code 0}.
     * This avoids depending on the non-public numeric ordering of the state enum.
     */
    @Override
    void prepareToBindMetrics(MeterRegistry registry) {
        super.prepareToBindMetrics(registry);
        MetricName stateMetricName = findStateMetricName();
        if (stateMetricName == null) {
            return;
        }
        MultiGauge gauge = MultiGauge.builder(meterName(stateMetricName))
            .tags(meterTags(stateMetricName))
            .description("The current state of the Kafka Streams client")
            .register(registry);
        List<Row<KafkaStreams.State>> rows = Arrays.stream(KafkaStreams.State.values())
            .map(state -> Row.of(Tags.of("state", state.name()), state,
                    candidate -> kafkaStreams.state() == candidate ? 1 : 0))
            .collect(Collectors.toList());
        gauge.register(rows);
        this.stateGauge = gauge;
    }

    private @Nullable MetricName findStateMetricName() {
        Map<MetricName, ? extends Metric> metrics = kafkaStreams.metrics();
        for (MetricName name : metrics.keySet()) {
            if (STREAM_METRICS_GROUP.equals(name.group()) && STATE_METRIC_NAME.equals(name.name())) {
                return name;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (stateGauge != null) {
            // remove the per-state gauges this binder registered
            stateGauge.register(emptyList());
            stateGauge = null;
        }
        super.close();
    }

}
