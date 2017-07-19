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
package org.springframework.boot.metrics.export.prometheus;

import io.prometheus.client.exporter.common.TextFormat;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.function.Supplier;

/**
 * Adds a router function to expose Prometheus metrics to a Prometheus scrape.
 */
public class PrometheusFunctions {
    public static HandlerFunction<ServerResponse> scrape(PrometheusMeterRegistry registry) {
        return (request) -> ServerResponse.ok().header("Content-Type", TextFormat.CONTENT_TYPE_004)
                .body(Mono.fromSupplier(registry::scrape), String.class);
    }
}
