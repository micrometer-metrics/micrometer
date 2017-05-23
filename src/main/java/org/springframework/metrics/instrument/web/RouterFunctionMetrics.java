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
import org.springframework.metrics.instrument.Tag;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class RouterFunctionMetrics {
    private final MeterRegistry registry;
    private BiFunction<ServerRequest, ServerResponse, Stream<Tag>> defaultTags = (ServerRequest request, ServerResponse response) ->
            Stream.of(method(request), status(response));

    public RouterFunctionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public RouterFunctionMetrics setDefaultTags(BiFunction<ServerRequest, ServerResponse, Stream<Tag>> defaultTags) {
        this.defaultTags = defaultTags;
        return this;
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer(String name) {
        return timer(name, Stream.empty());
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer(String name, Stream<Tag> tags) {
        return (request, next) -> {
            final long start = System.nanoTime();
            return next
                    .handle(request)
                    .doOnSuccess(response -> {
                        Stream<Tag> allTags = Stream.concat(tags, defaultTags.apply(request, response));
                        registry.timer(name, allTags).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    })
                    .doOnError(error -> {
                    });
        };
    }

    /**
     * @param request The HTTP request.
     * @return A "method" tag whose value is a capitalized method (e.g. GET).
     */
    public static Tag method(ServerRequest request) {
        return Tag.of("method", request.method().toString());
    }

    /**
     * @param response The HTTP response.
     * @return A "status" tag whose value is the numeric status code.
     */
    public static Tag status(ServerResponse response) {
        return Tag.of("status", response.statusCode().toString());
    }
}
