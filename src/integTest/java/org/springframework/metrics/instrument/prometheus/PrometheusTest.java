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
package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.stream.IntStream;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=8000",
        "endpoints.sensitive=false"
})
public class PrometheusTest {
    @Autowired
    TestRestTemplate restTemplate;

    private int numberOfRequests() {
        return 1;
    }

    @Test
    @Ignore
    public void executeRequestsForOneMinute() {
        Flux<String> flux = Flux.interval(Duration.ofSeconds(1))
                .map(input -> input + ": " + IntStream.of(1, numberOfRequests())
                        .map(n -> {
                            restTemplate.getForObject("/index", String.class);
                            return 1;
                        })
                        .sum()
                );

        flux.subscribe(System.out::println);
        flux.blockLast();
    }
}

@SpringBootApplication
@EnablePrometheusEndpoint
class PrometheusExample {
    @Bean
    CollectorRegistry registry() {
        CollectorRegistry registry = CollectorRegistry.defaultRegistry;
        registry.register(new StandardExports());
        registry.register(new MemoryPoolsExports());
        return registry;
    }
}

@RestController
class PrometheusExampleController {
    Counter counter = Counter.build("index_requests2", " ")
            .create()
            .register(); // registers with CollectorRegistry.defaultRegistry

    @GetMapping("/index")
    String index() {
        counter.inc();
        return "hello world";
    }
}