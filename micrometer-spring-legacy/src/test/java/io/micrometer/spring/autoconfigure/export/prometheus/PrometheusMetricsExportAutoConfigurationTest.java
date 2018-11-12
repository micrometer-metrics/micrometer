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
package io.micrometer.spring.autoconfigure.export.prometheus;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PrometheusMetricsExportAutoConfiguration}.
 *
 * @author Johnny Lim
 */
class PrometheusMetricsExportAutoConfigurationTest {

    private AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @Test
    void autoConfigureByDefault() {
        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThat(this.context.getBean(PrometheusMeterRegistry.class)).isNotNull();
    }

    @Test
    void autoConfigureDisabledByProperty() {
        EnvironmentTestUtils.addEnvironment(this.context, "management.metrics.export.prometheus.enabled=false");

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(PrometheusMeterRegistry.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void autoConfigurePrometheusPushGatewayDisabledByDefault() {
        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(PrometheusMetricsExportAutoConfiguration.PrometheusPushGatewayConfiguration.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void autoConfigurePrometheusPushGatewayEnabledByProperty() {
        EnvironmentTestUtils.addEnvironment(this.context, "management.metrics.export.prometheus.pushgateway.enabled=true");

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThat(this.context.getBean(PrometheusMetricsExportAutoConfiguration.PrometheusPushGatewayConfiguration.class)).isNotNull();
    }

    @Test
    void autoConfigurePrometheusPushGatewayDisabledByPrometheusEnabledProperty() {
        EnvironmentTestUtils.addEnvironment(this.context, "management.metrics.export.prometheus.enabled=false", "management.metrics.export.prometheus.pushgateway.enabled=true");

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(PrometheusMetricsExportAutoConfiguration.PrometheusPushGatewayConfiguration.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void autoConfigurePrometheusScrapeMvcEndpoint() {
        EnvironmentTestUtils.addEnvironment(this.context);

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThat(this.context.getBean(PrometheusScrapeMvcEndpoint.class)).isNotNull();
    }

    @Test
    void autoConfigurePrometheusScrapeMvcEndpointDisabledByEndpointsEnabledFalse() {
        EnvironmentTestUtils.addEnvironment(this.context, "endpoints.enabled=false");

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(PrometheusScrapeMvcEndpoint.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void autoConfigurePrometheusScrapeMvcEndpointDisabledByEndpointsPrometheusEnabledFalse() {
        EnvironmentTestUtils.addEnvironment(this.context, "endpoints.prometheus.enabled=false");

        registerAndRefresh(ClockConfiguration.class, PrometheusMetricsExportAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(PrometheusScrapeMvcEndpoint.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        this.context.register(configurationClasses);
        this.context.refresh();
    }

    @Configuration
    static class ClockConfiguration {

        @Bean
        public Clock clock() {
            return mock(Clock.class);
        }

    }

}
