package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * Spring Boot Actuator endpoint that outputs Prometheus metrics in a format that
 * can be scraped by the Prometheus server
 */
@ConfigurationProperties("endpoints.prometheus")
class PrometheusEndpoint extends AbstractEndpoint<ResponseEntity<String>> {

    // TODO what to do in the event that we have multiple registries?
    private final CollectorRegistry collectorRegistry;

    PrometheusEndpoint(CollectorRegistry collectorRegistry) {
        super("prometheus");
        this.collectorRegistry = collectorRegistry;
    }

    @Override
    public ResponseEntity<String> invoke() {
        try {
            Writer writer = new StringWriter();
            TextFormat.write004(writer, collectorRegistry.metricFamilySamples());
            return ResponseEntity.ok()
                    .header(CONTENT_TYPE, TextFormat.CONTENT_TYPE_004)
                    .body(writer.toString());
        } catch (IOException e) {
            // This actually never happens since StringWriter::write() doesn't throw any IOException
            throw new RuntimeException("Writing metrics failed", e);
        }
    }
}
