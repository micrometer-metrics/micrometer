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
import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class RouterFunctionMetrics {
    private final MeterRegistry registry;

    private String defaultName = "http_server_requests";

    // FIXME how best to inject the tag formatter and configurer?
    private WebMetricsTagConfigurer tagProvider = new DefaultWebMetricsTagConfigurer(new TagFormatter() {
    });

    public RouterFunctionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public RouterFunctionMetrics setDefaultName(String defaultName) {
        this.defaultName = defaultName;
        return this;
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer() {
        return timer(defaultName, Stream.empty());
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer(Stream<Tag> tags) {
        return timer(defaultName, tags);
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer(String name, Stream<Tag> tags) {
        return (request, next) -> {
            final long start = System.nanoTime();
            return next
                    .handle(request)
                    .doOnSuccess(response -> {
                        Stream<Tag> allTags = Stream.concat(tags, tagProvider.httpRequestTags(request, response, "", null));
                        registry.timer(name, allTags).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    });
        };
    }
}
