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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Integration tests for {@link MetricsAutoConfiguration}.
 *
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = MetricsAutoConfigurationIntegrationTest.MetricsApp.class)
public class MetricsAutoConfigurationIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RestTemplate external;

    @Autowired
    private TestRestTemplate loopback;

    @Autowired
    private MeterRegistry registry;

    @SuppressWarnings("unchecked")
    @Test
    public void restTemplateIsInstrumented() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(external).build();
        server.expect(once(), requestTo("/api/external"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
            "hello", MediaType.APPLICATION_JSON));

        assertThat(external.getForObject("/api/external", String.class)).isEqualTo("hello");

        assertThat(registry.get("http.client.requests").timer().count()).isEqualTo(1L);
    }

    @Test
    public void requestMappingIsInstrumented() {
        loopback.getForObject("/api/people", String.class);

        assertThat(registry.get("http.server.requests").timer().count()).isEqualTo(1L);
    }

    @Test
    public void automaticallyRegisteredBinders() {
        assertThat(context.getBeansOfType(MeterBinder.class).values())
            .hasAtLeastOneElementOfType(LogbackMetrics.class)
            .hasAtLeastOneElementOfType(JvmGcMetrics.class)
            .hasAtLeastOneElementOfType(JvmGcMetrics.class)
            .hasAtLeastOneElementOfType(JvmThreadMetrics.class)
            .hasAtLeastOneElementOfType(ClassLoaderMetrics.class)
            .hasAtLeastOneElementOfType(UptimeMetrics.class)
            .hasAtLeastOneElementOfType(ProcessorMetrics.class)
            .hasAtLeastOneElementOfType(FileDescriptorMetrics.class);
    }

    @Test
    public void registryCustomizersAreAppliedBeforeRegistryIsInjectableElsewhere() {
        registry.get("my.thing").tags("common", "tag").gauge();
    }

    @SpringBootApplication(scanBasePackages = "ignored")
    @Import(PersonController.class)
    static class MetricsApp {
        @Bean
        MockClock mockClock() {
            return new MockClock();
        }

        @Bean
        public MeterRegistryCustomizer commonTags() {
            return r -> r.config().commonTags("common", "tag");
        }

        @Bean
        public MyThing myBinder(MeterRegistry registry) {
            // this should have the common tag
            registry.gauge("my.thing", 0);
            return new MyThing();
        }

        @Bean
        public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
            return restTemplateBuilder.build();
        }

        private class MyThing {
        }

    }

    @RestController
    static class PersonController {
        @GetMapping("/api/people")
        String personName() {
            return "Jon";
        }
    }
}
