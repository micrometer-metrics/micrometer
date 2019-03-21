/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.export.prometheus;

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
 *
 * @author Jon Schneider
 */
@ConfigurationProperties("endpoints.prometheus")
public class PrometheusScrapeEndpoint extends AbstractEndpoint<ResponseEntity<String>> {

    private final CollectorRegistry collectorRegistry;

    public PrometheusScrapeEndpoint(CollectorRegistry collectorRegistry) {
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
