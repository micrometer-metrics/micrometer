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
package io.micrometer.spring.autoconfigure.jersey;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.jersey.JerseyAutoConfiguration;
import org.springframework.boot.autoconfigure.jersey.ResourceConfigCustomizer;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.jersey2.server.JerseyTagsProvider;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link JerseyServerMetricsAutoConfiguration}.
 *
 * @author Michael Weirauch
 * @author Michael Simons
 * @author Johnny Lim
 */
class JerseyServerMetricsAutoConfigurationTest {

    private final AnnotationConfigEmbeddedWebApplicationContext context = new AnnotationConfigEmbeddedWebApplicationContext();

    private final Class<?>[] commonClasses = {
        ResourceConfiguration.class, ServerPropertiesAutoConfiguration.class,
        EmbeddedServletContainerAutoConfiguration.class,
        JerseyAutoConfiguration.class, MetricsAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class,
        JerseyServerMetricsAutoConfiguration.class
    };

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void shouldOnlyBeActiveInWebApplicationContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(commonClasses);
        context.refresh();

        assertThatThrownBy(() -> context.getBean(ResourceConfigCustomizer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
        context.close();
    }

    @Test
    void shouldProvideAllNecessaryBeans() {
        registerAndRefresh();
        assertThat(context.getBean(JerseyTagsProvider.class)).isNotNull();
        assertThat(context.getBean(ResourceConfigCustomizer.class)).isNotNull();
    }

    @Test
    void shouldHonorExistingTagProvider() {
        registerAndRefresh(CustomJerseyTagsProviderConfiguration.class);
        assertThat(context.getBean(JerseyTagsProvider.class))
                .isExactlyInstanceOf(CustomJerseyTagsProvider.class);
    }

    @Test
    void httpRequestsAreTimed() throws InterruptedException {
        registerAndRefresh();
        doRequest();

        // NOTE: An immediate fetching doesn't seem to work as meter collection for Jersey is event-driven.
        TimeUnit.SECONDS.sleep(1);

        MeterRegistry registry = context.getBean(MeterRegistry.class);
        Timer timer = registry.get("http.server.requests").tag("uri", "/users/{id}").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    private void doRequest() {
        int port = context.getEmbeddedServletContainer().getPort();
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getForEntity(URI.create("http://localhost:" + port + "/users/3"), String.class);
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        EnvironmentTestUtils.addEnvironment(context, "server.port=0");

        List<Class<?>> classes = new ArrayList<>();
        Arrays.stream(configurationClasses).forEach(classes::add);
        Arrays.stream(commonClasses).forEach(classes::add);
        context.register(classes.toArray(new Class<?>[0]));
        context.refresh();
    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        public MeterRegistry meterRegistry() {
            return mock(MeterRegistry.class);
        }

    }

    @Configuration
    static class ResourceConfiguration {

        @Bean
        ResourceConfig resourceConfig() {
            return new ResourceConfig().register(new TestResource());
        }

        @Path("/users")
        public class TestResource {

            @GET
            @Path("/{id}")
            public String getUser(@PathParam("id") String id) {
                return id;
            }

        }

    }

    @Configuration
    static class CustomJerseyTagsProviderConfiguration {

        @Bean
        JerseyTagsProvider customJerseyTagsProvider() {
            return new CustomJerseyTagsProvider();
        }

    }

    static class CustomJerseyTagsProvider implements JerseyTagsProvider {

        @Override
        public Iterable<Tag> httpRequestTags(RequestEvent event) {
            return null;
        }

        @Override
        public Iterable<Tag> httpLongRequestTags(RequestEvent event) {
            return null;
        }

    }

}
