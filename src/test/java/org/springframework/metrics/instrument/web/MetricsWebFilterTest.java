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
package org.springframework.metrics.instrument.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.instrument.IdentityTagFormatter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

class MetricsWebFilterTest {
    private MeterRegistry registry = new SimpleMeterRegistry();
    private WebTestClient client;

    @BeforeEach
    void before() {
        client = WebTestClient.bindToController(new Controller2())
                .webFilter(new MetricsWebFilter(registry, new WebfluxTagConfigurer(new IdentityTagFormatter()), "http_server_requests"))
                .build();
    }

    @Test
    void metricsGatheredWhenControllerIsTimed() throws Exception {
        client.get().uri("/api/c2/10").exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(r -> assertThat(new String(r.getResponseBody())).isEqualTo("10"));

        assertThat(registry.findMeter(Timer.class, "http_server_requests"))
                .hasValueSatisfying(t -> {
                    assertThat(t.getTags())
                            .contains(Tag.of("uri", "api/c2/{id}"))
                            .contains(Tag.of("status", "200"));
                    assertThat(t.count()).isEqualTo(1);
                });
    }

    @RestController
    @Timed
    @RequestMapping("/api/c2")
    static class Controller2 {
        @GetMapping("/{id}")
        public Flux<String> successful(@PathVariable Long id) {
            return Flux.just(id.toString());
        }
    }
}
