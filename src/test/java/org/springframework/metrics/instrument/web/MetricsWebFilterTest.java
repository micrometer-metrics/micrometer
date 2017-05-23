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
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.IdentityTagFormatter;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleTimer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MetricsWebFilterTest {
    private MeterRegistry registry;
    private WebTestClient client;

    @BeforeEach
    void before() {
        registry = mock(MeterRegistry.class);
        client = WebTestClient.bindToController(new Controller2())
                .webFilter(new MetricsWebFilter(registry, new WebfluxTagConfigurer(new IdentityTagFormatter()), "http_server_requests"))
                .build();
    }

    @Test
    void metricsGatheredWhenControllerIsTimed() throws Exception {
        SimpleTimer timer = expectTimer();
        client.get().uri("/api/c2/10").exchange()
                .expectStatus().isOk()
                .expectBody().consumeAsStringWith(b -> assertThat(b).isEqualTo("10"));

        assertTags(
                Tag.of("uri", "api/c2/{id}")
                // FIXME doOnSuccess happens before status code is determined
//                Tag.of("status", "200")
        );
        assertThat(timer.count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private void assertTags(Tag... match) {
        ArgumentCaptor<Stream> tags = ArgumentCaptor.forClass(Stream.class);
        verify(registry).timer(anyString(), tags.capture());
        assertThat((List) tags.getValue().collect(Collectors.toList())).contains((Object[]) match);
    }

    private SimpleTimer expectTimer() {
        SimpleTimer timer = new SimpleTimer("http_server_requests");

        //noinspection unchecked
        when(registry.timer(eq("http_server_requests"), any(Stream.class))).thenReturn(timer);
        return timer;
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
