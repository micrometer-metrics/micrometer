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
package io.micrometer.spring.web.jersey2.server;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.time.Duration;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = JerseyServerMetricsTest.JerseyApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.jersey.type=filter",
        "spring.jersey.filter.order=-2147483648"
})
public class JerseyServerMetricsTest {
    @Autowired
    private TestRestTemplate client;

    @Autowired
    private MeterRegistry registry;

    @Issue("#486")
    @Test
    public void jerseyWeb() {
        client.getForObject("/ping/1", String.class);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(250))
                .retryExceptions(MeterNotFoundException.class)
                .build();

        Retry.of("searchForTimer", retryConfig).executeRunnable(() ->
                registry.get("http.server.requests")
                        .tag("uri", "/ping/{id}")
                        .timer()
        );
    }

    @SpringBootApplication(scanBasePackages = "ignore")
    @Import({JerseyConfig.class, PingResource.class})
    public static class JerseyApp {
    }

    public static class JerseyConfig extends ResourceConfig {
        public JerseyConfig() {
            this.register(PingResource.class);
        }
    }

    @Path("/ping")
    public static class PingResource {
        @GET
        @Path("/{id}")
        public String ping(@PathParam("id") String id) {
            return id;
        }
    }
}
