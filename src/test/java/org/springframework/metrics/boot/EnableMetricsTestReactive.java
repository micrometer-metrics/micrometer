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
package org.springframework.metrics.boot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.metrics.instrument.web.WebMetricsTagProvider;
import org.springframework.metrics.instrument.web.WebfluxMetricsWebFilter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.web-application-type=reactive"
})
public class EnableMetricsTestReactive {
    @Autowired
    TestRestTemplate loopback;

    @Autowired
    SimpleMeterRegistry registry;

    // TODO put MethodHandler equivalent in RequestMappingInfoHandlerMapping.handleMatch where attributes are set?
    @Test
    public void reactiveRequestMappingIsInstrumented() {
        assertThat(loopback.getForObject("/api/echo/hi", String.class)).isEqualTo("hi");

        assertThat(registry.findMeter(Timer.class, "http_server_requests", "uri", "api_echo_-word-"))
                .containsInstanceOf(Timer.class)
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @SpringBootApplication
    @Import(EchoController.class)
    @EnableMetrics
    static class MetricsAppReactive {
        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        public WebfluxMetricsWebFilter webfluxMetrics(WebMetricsTagProvider provider) {
            return new WebfluxMetricsWebFilter(registry(), provider, "http_server_requests");
        }
    }

    @RestController
    static class EchoController {
        @Timed
        @GetMapping("/api/echo/{word}")
        public Flux<String> successful(@PathVariable String word) {
            return Flux.just(word);
        }
    }
}