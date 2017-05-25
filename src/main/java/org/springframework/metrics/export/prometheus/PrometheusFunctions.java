package org.springframework.metrics.export.prometheus;

import io.prometheus.client.exporter.common.TextFormat;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Adds a router function to expose Prometheus metrics to a Prometheus scrape.
 */
public class PrometheusFunctions {
    public static HandlerFunction<ServerResponse> scrape(PrometheusMeterRegistry registry) {
        return (request) -> ServerResponse.ok().header("Content-Type", TextFormat.CONTENT_TYPE_004)
                .body(Mono.fromSupplier(() -> {
                    Writer writer = new StringWriter();
                    try {
                        TextFormat.write004(writer, registry.getCollectorRegistry().metricFamilySamples());
                    } catch (IOException e) {
                        // This actually never happens since StringWriter::write() doesn't throw any IOException
                        throw new RuntimeException(e);
                    }
                    return writer.toString();
                }), String.class);
    }
}
