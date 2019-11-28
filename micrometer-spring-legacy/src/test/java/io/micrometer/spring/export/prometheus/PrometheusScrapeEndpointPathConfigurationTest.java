/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.export.prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.EndpointWebMvcAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.ManagementServerPropertiesAutoConfiguration;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Adapted from Spring Boot's <a href="https://github.com/spring-projects/spring-boot/blob/10a5cef4ef33e7c86d18e1f92793c2aaa57d5a82/spring-boot-actuator/src/test/java/org/springframework/boot/actuate/autoconfigure/MvcEndpointPathConfigurationTests.java">MvcEndpointPathConfigurationTests</a>.
 */
class PrometheusScrapeEndpointPathConfigurationTest {

    private AnnotationConfigWebApplicationContext context;

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void pathCanBeConfigured() {
        this.context = new AnnotationConfigWebApplicationContext();
        this.context.register(TestConfiguration.class);
        this.context.setServletContext(new MockServletContext());
        EnvironmentTestUtils.addEnvironment(this.context,
                "endpoints.prometheus.path:/custom/path",
                "endpoints.prometheus.enabled:true");
        this.context.refresh();
        assertThat(getPath()).isEqualTo("/custom/path");
    }

    private String getPath() {
        return ((MvcEndpoint) this.context.getBean(PrometheusScrapeMvcEndpoint.class)).getPath();
    }

    @Configuration
    @ImportAutoConfiguration({ PrometheusMetricsExportAutoConfiguration.class, EndpointAutoConfiguration.class, ManagementServerPropertiesAutoConfiguration.class,
            ServerPropertiesAutoConfiguration.class, EndpointWebMvcAutoConfiguration.class })
    protected static class TestConfiguration {

        @Bean
        public Clock clock() {
            return mock(Clock.class);
        }
    }
}
