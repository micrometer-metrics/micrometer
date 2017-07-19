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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

/**
 * @author Jon Schneider
 */
class RouterFunctionMetricsTest {
    private MeterRegistry registry = new SimpleMeterRegistry();

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

        routes.route(request)
                .block() // block for handler filter function
                .handle(request)
                .block(); // block for ServerResponse

        assertThat(registry.findMeter(Timer.class, "http_server_requests"))
                .hasValueSatisfying(t -> assertThat(t.getTags()).contains(Tag.of("status", "200")));
    }
}
