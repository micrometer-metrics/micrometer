/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.samples;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.core.instrument.MeterFilterConfigProperties;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.micrometer.spring.samples.components")
@EnableScheduling
public class PrometheusSample {
    public static void main(String[] args) {
        new SpringApplicationBuilder(PrometheusSample.class).profiles("prometheus").run(args);
    }

    @Bean
    @ConfigurationProperties("clint.metrics")
    public MeterFilterConfigProperties meterFilterConfigProperties(){
        return new MeterFilterConfigProperties();
    }

    @Bean
    public MetricsExporter prometheusExporter(PrometheusConfig config,
                                              MeterFilterConfigProperties meterFilterConfigProperties,
                                              CollectorRegistry collectorRegistry, Clock clock) {
        return () -> {
            PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(config, collectorRegistry, clock);
            prometheusMeterRegistry.config().meterFilter(meterFilterConfigProperties);
            return  prometheusMeterRegistry;
        };
    }


}
