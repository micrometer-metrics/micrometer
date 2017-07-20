/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.metrics.web;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.IdentityTagFormatter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class MetricsWebFilterTest {
    private MeterRegistry registry = new SimpleMeterRegistry();
    private WebTestClient client;

    @BeforeEach
    void before() {
        client = WebTestClient.bindToController(new FluxController())
                .webFilter(new MetricsWebFilter(registry, new WebfluxTagConfigurer(new IdentityTagFormatter()), "http_server_requests"))
                .build();
    }

    @Test
    @Disabled
    void metricsGatheredWhenControllerIsTimed() throws Exception {
        client.get().uri("/api/c2/10").exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(r -> {
                    assertThat(new String(r.getResponseBody())).isEqualTo("10");

                    assertThat(registry.findMeter(Timer.class, "http_server_requests"))
                            .hasValueSatisfying(t -> {
                                assertThat(t.getTags())
                                        .contains(Tag.of("uri", "api/thing/{id}"))
                                        .contains(Tag.of("status", "200"));
                                assertThat(t.count()).isEqualTo(1);
                            });
                });
    }

    @RestController
    @Timed
    @RequestMapping("/api/thing")
    class FluxController {
        @GetMapping("/{id}")
        public Flux<String> successful(@PathVariable Long id) {
            return Flux.just(id.toString());
        }
    }
}
