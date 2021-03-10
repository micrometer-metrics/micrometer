/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.boot2.samples;

import io.micrometer.boot2.samples.components.PersonController;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * Sample for filter-supported Prometheus scrape endpoint.
 *
 * @author Johnny Lim
 */
@SpringBootApplication(scanBasePackageClasses = PersonController.class)
@EnableScheduling
public class PrometheusFilteredSample {
    public static void main(String[] args) {
        new SpringApplicationBuilder(PrometheusFilteredSample.class).profiles("prometheus").run(args);
    }

    @Component
    @RestControllerEndpoint(id = "filteredPrometheus")
    static class FilteredPrometheusScrapeEndpoint  {

        private final PrometheusMeterRegistry meterRegistry;

        public FilteredPrometheusScrapeEndpoint(PrometheusMeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @GetMapping(produces = {TextFormat.CONTENT_TYPE_004, TextFormat.CONTENT_TYPE_OPENMETRICS_100})
        public ResponseEntity<String> scrape(@RequestParam(required = false, name = "includedNames") Set<String> includedNames,
                                             @RequestHeader(required = false, name = "Accept") String acceptHeader) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(TextFormat.chooseContentType(acceptHeader)))
                    .body(this.meterRegistry.scrape(TextFormat.chooseContentType(acceptHeader), includedNames));
        }
    }
}
