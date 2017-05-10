package org.springframework.metrics.instrument.web;

import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class RouterFunctionMetrics {
    private final MeterRegistry registry;

    private String defaultTagName = "http-request";
    private WebMetricsTagProvider tagProvider = new DefaultWebMetricsTagProvider();

    public RouterFunctionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public RouterFunctionMetrics setDefaultTagName(String defaultTagName) {
        this.defaultTagName = defaultTagName;
        return this;
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer() {
        return timer(defaultTagName, Stream.empty());
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> timer(String name, Stream<Tag> tags) {
        return (request, next) -> {
            final long start = System.nanoTime();
            return next
                    .handle(request)
                    .doOnSuccess(response -> {
                        Stream<Tag> allTags = Stream.concat(tags, tagProvider.httpRequestTags(request, response, "", null, null));
                        registry.timer(name, allTags).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    });
        };
    }
}
