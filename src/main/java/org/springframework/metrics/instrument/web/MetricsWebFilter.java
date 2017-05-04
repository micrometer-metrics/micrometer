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

import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MetricsWebFilter implements WebFilter {
    private final MeterRegistry registry;

    public MetricsWebFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final long start = System.nanoTime();
        Mono<Void> filtered = chain.filter(exchange);
        // FIXME doesn't work
        filtered.subscribe(done -> {
            registry.timer("http-request", Stream.empty()).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        });
        return filtered;
    }
}
