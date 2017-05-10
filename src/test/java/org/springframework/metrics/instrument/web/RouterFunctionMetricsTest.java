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
                .filter(metrics.timer());

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
        SimpleTimer timer = new SimpleTimer();

        //noinspection unchecked
        when(registry.timer(eq("http-request"), any(Stream.class))).thenReturn(timer);
        return timer;
    }
}
