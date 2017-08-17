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
package io.micrometer.spring.export.prometheus;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.MeterRegistryConfigurer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EnablePrometheusMetricsTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    PrometheusMeterRegistry registry;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void meterRegistry() {
        assertThat(context.getBean(MeterRegistry.class))
                .isInstanceOf(PrometheusMeterRegistry.class);
    }

    @Test
    public void commonTags() {
        restTemplate.getForObject("/api/people", String.class);
        assertThat(registry.scrape()).contains("http_server_requests", "stack", "region");
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    @EnablePrometheusMetrics
    @Import(PersonController.class)
    static class PrometheusApp {
        @Bean
        public MeterRegistryConfigurer registryConfigurer() {
            return registry -> registry.config().commonTags("stack", "prod", "region", "us-east-1");
        }
    }

    @RestController
    static class PersonController {
        @Timed
        @GetMapping("/api/people")
        Set<String> personName() {
            return Collections.singleton("Jon");
        }
    }
}
