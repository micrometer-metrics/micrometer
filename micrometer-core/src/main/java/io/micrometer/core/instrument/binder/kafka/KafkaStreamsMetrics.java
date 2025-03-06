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
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;
import org.apache.kafka.common.Metric;
import org.apache.kafka.streams.KafkaStreams;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Kafka Streams metrics binder. This should be closed on application shutdown to clean up
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
@NonNullFields
public class KafkaStreamsMetrics extends KafkaMetrics {

    /**
     * {@link KafkaStreams} metrics binder
     * @param kafkaStreams instance to be instrumented
     * @param tags additional tags
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags) {
        super(kafkaStreams::metrics, tags);
    }

    /**
     * {@link KafkaStreams} metrics binder
     * @param kafkaStreams instance to be instrumented
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams) {
        super(kafkaStreams::metrics);
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
    }

}
