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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics.METRIC_NAME_PREFIX;
import static org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsMetricsTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private static final String STATE_METER_NAME = "kafka.stream.state";

    private Tags tags = Tags.of("app", "myapp", "version", "1");

    @Test
    void shouldCreateMeters() {
        // tag::example[]
        try (KafkaStreams kafkaStreams = createStreams();
                KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);
            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getName())
                .allMatch(s -> s.startsWith(METRIC_NAME_PREFIX));
        }
        // end::example[]
    }

    @Test
    void shouldCreateMetersWithTags() {
        try (KafkaStreams kafkaStreams = createStreams();
                KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams, tags)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));
        }
    }

    @Test
    void shouldCreateMetersWithTagsAndCustomScheduler() {
        ScheduledExecutorService customScheduler = Executors.newScheduledThreadPool(1);
        try (KafkaStreams kafkaStreams = createStreams();
                KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams, tags, customScheduler)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).hasSizeGreaterThan(0)
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));

            metrics.close();
            assertThat(customScheduler.isShutdown()).isFalse();
        }
        finally {
            customScheduler.shutdownNow();
            assertThat(customScheduler.isShutdown()).isTrue();
        }
    }

    @Test
    void shouldReportApplicationStateAsStateSet() {
        try (KafkaStreams kafkaStreams = createStreams();
                KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            List<Gauge> stateGauges = registry.find(STATE_METER_NAME).gauges().stream().collect(Collectors.toList());
            // one time series per possible state
            assertThat(stateGauges).extracting(gauge -> gauge.getId().getTag("state"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(KafkaStreams.State.values()).map(Enum::name).collect(Collectors.toList()));
            // exactly the current state reports 1, everything else 0
            String currentState = kafkaStreams.state().name();
            assertThat(stateGauges).allSatisfy(gauge -> {
                double expected = gauge.getId().getTag("state").equals(currentState) ? 1 : 0;
                assertThat(gauge.value()).isEqualTo(expected);
            });
            assertThat(stateGauges).filteredOn(gauge -> gauge.value() == 1)
                .extracting(gauge -> gauge.getId().getTag("state"))
                .containsExactly(currentState);
        }
    }

    @Test
    void stateGaugeShouldCarryConfiguredTags() {
        try (KafkaStreams kafkaStreams = createStreams();
                KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams, tags)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.find(STATE_METER_NAME).gauges()).isNotEmpty()
                .allSatisfy(gauge -> assertThat(gauge.getId().getTag("app")).isEqualTo("myapp"));
        }
    }

    @Test
    void closeShouldRemoveStateGauges() {
        MeterRegistry registry = new SimpleMeterRegistry();
        try (KafkaStreams kafkaStreams = createStreams()) {
            KafkaStreamsMetrics metrics = new KafkaStreamsMetrics(kafkaStreams);
            metrics.bindTo(registry);
            assertThat(registry.find(STATE_METER_NAME).gauges()).isNotEmpty();

            metrics.close();

            assertThat(registry.find(STATE_METER_NAME).gauges()).isEmpty();
        }
    }

    private KafkaStreams createStreams() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("input").to("output");
        Properties streamsConfig = new Properties();
        streamsConfig.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        streamsConfig.put(APPLICATION_ID_CONFIG, "app");
        return new KafkaStreams(builder.build(), streamsConfig);
    }

}
