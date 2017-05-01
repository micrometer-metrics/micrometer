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