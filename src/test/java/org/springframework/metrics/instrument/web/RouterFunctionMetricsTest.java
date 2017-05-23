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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.simple.SimpleTimer;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;


class RouterFunctionMetricsTest {
    private MeterRegistry registry = mock(MeterRegistry.class);

    private MockServerRequest request = MockServerRequest.builder()
            .uri(URI.create("/person/1"))
            .pathVariable("id", "1")
            .build();

    @SuppressWarnings("unchecked")
    @Test
    void handlerFilterFunctions() {
        RouterFunctionMetrics metrics = new RouterFunctionMetrics(registry);

        RouterFunction<ServerResponse> routes = RouterFunctions
                .route(GET("/person/{id}").and(accept(APPLICATION_JSON)), request -> ServerResponse.ok().build())
                .filter(metrics.timer("http_server_requests"));

        expectTimer();

        routes.route(request)
                .block() // block for handler filter function
                .handle(request)
                .block(); // block for ServerResponse

        assertTags(Tag.of("status", "200"));
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
}
