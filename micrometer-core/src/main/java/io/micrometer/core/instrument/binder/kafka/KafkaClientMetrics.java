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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Metric;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Kafka Client metrics binder. This should be closed on application shutdown to clean up
 * resources.
 * <p>
 * It is based on the Kafka client's {@code metrics()} method returning a {@link Metric}
 * map.
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
@NullMarked
public class KafkaClientMetrics extends KafkaMetrics {

    /**
     * Kafka {@link Producer} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaClientMetrics} to reduce resource usage by reducing the number of
     * threads if there will be many instances.
     * @param kafkaProducer producer instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @since 1.14.0
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags, ScheduledExecutorService scheduler) {
        super(kafkaProducer::metrics, tags, scheduler);
    }

    /**
     * Kafka {@link Producer} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaClientMetrics} to reduce resource usage by reducing the number of
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
     * @param kafkaProducer producer instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @param refreshInterval interval of discovering new/removed/recreated metrics by the
     * Kafka Client
     * @since 1.16.0
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags, ScheduledExecutorService scheduler,
            Duration refreshInterval) {
        super(kafkaProducer::metrics, tags, scheduler, refreshInterval);
    }

    /**
     * Kafka {@link Producer} metrics binder
     * @param kafkaProducer producer instance to be instrumented
     * @param tags additional tags
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags) {
        super(kafkaProducer::metrics, tags);
    }

    /**
     * Kafka {@link Producer} metrics binder
     * @param kafkaProducer producer instance to be instrumented
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer) {
        super(kafkaProducer::metrics);
    }

    /**
     * Kafka {@link Consumer} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaClientMetrics} to reduce resource usage by reducing the number of
     * threads if there will be many instances.
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @since 1.14.0
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags, ScheduledExecutorService scheduler) {
        super(kafkaConsumer::metrics, tags, scheduler);
    }

    /**
     * Kafka {@link Consumer} metrics binder. The lifecycle of the custom scheduler passed
     * is the responsibility of the caller. It will not be shut down when this instance is
     * {@link #close() closed}. A scheduler can be shared among multiple instances of
     * {@link KafkaClientMetrics} to reduce resource usage by reducing the number of
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
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @param refreshInterval interval of discovering new/removed/recreated metrics by the
     * Kafka Client
     * @since 1.16.0
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags, ScheduledExecutorService scheduler,
            Duration refreshInterval) {
        super(kafkaConsumer::metrics, tags, scheduler, refreshInterval);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags additional tags
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags) {
        super(kafkaConsumer::metrics, tags);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     * @param kafkaConsumer consumer instance to be instrumented
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer) {
        super(kafkaConsumer::metrics);
    }

    /**
     * Kafka {@link AdminClient} metrics binder. The lifecycle of the custom scheduler
     * passed is the responsibility of the caller. It will not be shut down when this
     * instance is {@link #close() closed}. A scheduler can be shared among multiple
     * instances of {@link KafkaClientMetrics} to reduce resource usage by reducing the
     * number of threads if there will be many instances.
     * @param adminClient instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @since 1.14.0
     */
    public KafkaClientMetrics(AdminClient adminClient, Iterable<Tag> tags, ScheduledExecutorService scheduler) {
        super(adminClient::metrics, tags, scheduler);
    }

    /**
     * Kafka {@link AdminClient} metrics binder. The lifecycle of the custom scheduler
     * passed is the responsibility of the caller. It will not be shut down when this
     * instance is {@link #close() closed}. A scheduler can be shared among multiple
     * instances of {@link KafkaClientMetrics} to reduce resource usage by reducing the
     * number of threads if there will be many instances.
     * <p>
     * The refresh interval governs how frequently Micrometer should call the Kafka
     * Client's Metrics API to discover new metrics to register and discard old ones since
     * the Kafka Client can add/remove/recreate metrics on-the-fly. Please notice that
     * this is not for fetching values for already registered metrics but for updating the
     * list of registered metrics when the Kafka Client adds/removes/recreates them. It is
     * the responsibility of the caller to choose the right value since this process can
     * be expensive and metrics can appear and disappear without being published if the
     * interval is not chosen appropriately.
     * @param adminClient instance to be instrumented
     * @param tags additional tags
     * @param scheduler custom scheduler to check and bind metrics
     * @param refreshInterval interval of discovering new/removed/recreated metrics by the
     * Kafka Client
     * @since 1.16.0
     */
    public KafkaClientMetrics(AdminClient adminClient, Iterable<Tag> tags, ScheduledExecutorService scheduler,
            Duration refreshInterval) {
        super(adminClient::metrics, tags, scheduler, refreshInterval);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     * @param adminClient instance to be instrumented
     * @param tags additional tags
     */
    public KafkaClientMetrics(AdminClient adminClient, Iterable<Tag> tags) {
        super(adminClient::metrics, tags);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     * @param adminClient instance to be instrumented
     */
    public KafkaClientMetrics(AdminClient adminClient) {
        super(adminClient::metrics);
    }

}
