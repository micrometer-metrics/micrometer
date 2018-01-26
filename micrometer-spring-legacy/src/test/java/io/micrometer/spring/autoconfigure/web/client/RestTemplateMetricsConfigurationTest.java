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
package io.micrometer.spring.autoconfigure.web.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RestTemplateMetricsConfigurationTest.ClientApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "security.ignored=/**")
public class RestTemplateMetricsConfigurationTest {
    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MetricsProperties metricsProperties;

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private RestTemplate client;

    @Before
    public void before() {
        client = restTemplateBuilder
            .rootUri("http://localhost:" + port)
            .build();
    }

    @Test
    public void restTemplatesCreatedWithBuilderAreInstrumented() {
        client.getForObject("/it/1", String.class);
        registry.get("http.client.requests").meter();
    }

    @Test
    public void afterMaxUrisReachedFurtherUrisAreDenied() {
        int maxUriTags = metricsProperties.getWeb().getClient().getMaxUriTags();
        for(int i = 0; i < maxUriTags + 10; i++) {
            client.getForObject("/it/" + i, String.class);
        }

        assertThat(registry.get("http.client.requests").meters()).hasSize(maxUriTags);
    }

    @SpringBootApplication(scanBasePackages = "ignore")
    @Import(SampleController.class)
    static class ClientApp {
        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.build();
        }
    }

    @RestController
    static class SampleController {
        @GetMapping("/it/{id}")
        public String it(@PathVariable String id) {
            return id;
        }
    }
}