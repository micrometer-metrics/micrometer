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
