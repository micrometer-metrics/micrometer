/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.docs.metrics;

import io.micrometer.core.instrument.HighCardinalityTagsDetector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Doc examples for {@link HighCardinalityTagsDetector}.
 *
 * @author Jonatan Ivanov
 */
class HighCardinalityTagsDetectorTests {

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void registryIntegration() {
        // tag::registry_integration_default[]
        registry.config().withHighCardinalityTagsDetector();
        // end::registry_integration_default[]

        // tag::registry_integration_factory[]
        registry.config().withHighCardinalityTagsDetector(HighCardinalityTagsDetector::new);
        // end::registry_integration_factory[]

        // @formatter:off
        // tag::registry_integration_builder[]
        registry.config().withHighCardinalityTagsDetector(r ->
            new HighCardinalityTagsDetector.Builder(r)
                .threshold(1000) // 1000 different meters with the same name
                .delay(Duration.ofMinutes(5)) // check ~every 5 minutes
                .highCardinalityMeterInfoConsumer(info -> alert("Nooo!"))
                .build()
        );
        // end::registry_integration_builder[]
        // @formatter:on
    }

    @Test
    void oneTimeCheck() {
        // tag::one_time_check[]
        try (HighCardinalityTagsDetector detector = new HighCardinalityTagsDetector.Builder(registry).threshold(10)
            .build()) {
            // Create meters with a high cardinality tag (uid)
            for (int i = 0; i < 15; i++) {
                registry.counter("requests", "uid", String.valueOf(i)).increment();
            }

            assertThat(detector.findFirst()).isNotEmpty().get().isEqualTo("requests");
            assertThat(detector.findFirstHighCardinalityMeterInfo()).isNotEmpty().get().satisfies(info -> {
                assertThat(info.getName()).isEqualTo("requests");
                assertThat(info.getCount()).isEqualTo(15);
            });

        }
        // detector.close() is implicit here but don't forget to close it otherwise!
        // end::one_time_check[]
    }

    @Test
    void customConsumer() {
        for (int i = 0; i < 15; i++) {
            registry.counter("requests", "uid", String.valueOf(i)).increment();
        }

        // @formatter:off
        // tag::custom_consumer_config[]
        registry.config().withHighCardinalityTagsDetector(r ->
            new HighCardinalityTagsDetector.Builder(r).threshold(10)
                .highCardinalityMeterInfoConsumer(this::recordHighCardinalityEvent)
                .build()
        );
        // end::custom_consumer_config[]
        // @formatter:on

        await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(registry.get("highCardinality.detections").counter().count()).isEqualTo(1));
    }

    // tag::custom_consumer[]
    void recordHighCardinalityEvent(HighCardinalityTagsDetector.HighCardinalityMeterInfo info) {
        alert("High cardinality detected in " + info.getName() + " with " + info.getCount() + " meters!");
        registry.counter("highCardinality.detections", "meter", info.getName()).increment();
    }
    // end::custom_consumer[]

    private void alert(String message) {
        System.out.println(message);
    }

}
