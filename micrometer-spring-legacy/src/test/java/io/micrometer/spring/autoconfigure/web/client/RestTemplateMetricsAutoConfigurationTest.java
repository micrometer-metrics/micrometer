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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RestTemplateMetricsAutoConfigurationTest.ClientApp.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "management.port=-1", // Disable the entire Spring Boot actuator, so that it does not get needlessly instrumented
        "security.ignored=/**",
})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class RestTemplateMetricsAutoConfigurationTest {
    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MetricsProperties metricsProperties;

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private AsyncRestTemplate asyncClient;

    private String rootUri;

    private RestTemplate client;

    @Before
    public void before() {
        rootUri = "http://localhost:" + port;
        client = restTemplateBuilder
                .rootUri(rootUri)
                .build();
    }

    @Test
    public void restTemplatesCreatedWithBuilderAreInstrumented() {
        client.getForObject("/it/1", String.class);
        assertThat(registry.get("http.client.requests").meters()).hasSize(1);
    }

    @Test
    public void asyncRestTemplatesInContextAreInstrumented() throws Exception {
        // therefore a full absolute URI is used
        ListenableFuture<ResponseEntity<String>> future = asyncClient.getForEntity(rootUri + "/it/2", String.class);

        final CyclicBarrier barrier = new CyclicBarrier(2);
        future.addCallback(result -> {
                    try {
                        barrier.await();
                    } catch (Throwable e) {
                        fail("barrier broken", e);
                    }
                },
                result -> fail("should not have failed"));

        future.get();

        barrier.await();
        assertThat(registry.get("http.client.requests").timer().count()).isEqualTo(1);
    }

    @Test
    public void afterMaxUrisReachedFurtherUrisAreDenied() {
        int maxUriTags = metricsProperties.getWeb().getClient().getMaxUriTags();
        for (int i = 0; i < maxUriTags + 10; i++) {
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
        public AsyncRestTemplate asyncRestTemplate() {
            final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setTaskExecutor(new SimpleAsyncTaskExecutor());
            return new AsyncRestTemplate(requestFactory);
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
