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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.hystrix.HystrixMetricsBinder;
import io.micrometer.spring.SpringEnvironmentMeterFilter;
import io.micrometer.spring.autoconfigure.export.CompositeMeterRegistryConfiguration;
import io.micrometer.spring.autoconfigure.export.atlas.AtlasExportConfiguration;
import io.micrometer.spring.autoconfigure.export.datadog.DatadogExportConfiguration;
import io.micrometer.spring.autoconfigure.export.ganglia.GangliaExportConfiguration;
import io.micrometer.spring.autoconfigure.export.graphite.GraphiteExportConfiguration;
import io.micrometer.spring.autoconfigure.export.influx.InfluxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.jmx.JmxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.newrelic.NewRelicExportConfiguration;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusExportConfiguration;
import io.micrometer.spring.autoconfigure.export.signalfx.SignalFxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleExportConfiguration;
import io.micrometer.spring.autoconfigure.export.statsd.StatsdExportConfiguration;
import io.micrometer.spring.autoconfigure.jersey2.server.JerseyServerMetricsConfiguration;
import io.micrometer.spring.autoconfigure.web.client.RestTemplateMetricsConfiguration;
import io.micrometer.spring.autoconfigure.web.servlet.ServletMetricsConfiguration;
import io.micrometer.spring.autoconfigure.web.tomcat.TomcatMetricsConfiguration;
import io.micrometer.spring.integration.SpringIntegrationMetrics;
import io.micrometer.spring.scheduling.ScheduledMethodMetrics;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.support.management.IntegrationManagementConfigurer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Micrometer-based metrics.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(Timed.class)
@EnableConfigurationProperties(MetricsProperties.class)
@Import({
    // default binders, apply customizers and binders to newly-created registries
    MeterBindersConfiguration.class, MeterRegistryPostProcessor.class,

    // default instrumentation
    ServletMetricsConfiguration.class, RestTemplateMetricsConfiguration.class,
    TomcatMetricsConfiguration.class, JerseyServerMetricsConfiguration.class,

    // registry implementations
    AtlasExportConfiguration.class, DatadogExportConfiguration.class,
    GangliaExportConfiguration.class, GraphiteExportConfiguration.class,
    InfluxExportConfiguration.class, NewRelicExportConfiguration.class,
    JmxExportConfiguration.class, StatsdExportConfiguration.class,
    PrometheusExportConfiguration.class, SimpleExportConfiguration.class,
    SignalFxExportConfiguration.class,

    // conditionally build a composite registry out of more than one registry present
    CompositeMeterRegistryConfiguration.class
})
public class MetricsAutoConfiguration {
    @Bean
    @Order(0)
    MeterRegistryCustomizer springEnvironmentMeterFilter(Environment environment) {
        return r -> r.config().meterFilter(new SpringEnvironmentMeterFilter(environment));
    }

    /**
     * If AOP is not enabled, scheduled interception will not work.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
    public ScheduledMethodMetrics metricsSchedulingAspect(MeterRegistry registry) {
        return new ScheduledMethodMetrics(registry);
    }

    @Bean
    @ConditionalOnClass(name = "com.netflix.hystrix.strategy.HystrixPlugins")
    @ConditionalOnProperty(value = "management.metrics.export.hystrix.enabled", matchIfMissing = true)
    public HystrixMetricsBinder hystrixMetricsBinder() {
        return new HystrixMetricsBinder();
    }

    @Configuration
    @ConditionalOnClass(EnableIntegrationManagement.class)
    static class MetricsIntegrationConfiguration {

        @Bean(name = IntegrationManagementConfigurer.MANAGEMENT_CONFIGURER_NAME)
        @ConditionalOnMissingBean(value = IntegrationManagementConfigurer.class, name = IntegrationManagementConfigurer.MANAGEMENT_CONFIGURER_NAME, search = SearchStrategy.CURRENT)
        public IntegrationManagementConfigurer integrationManagementConfigurer() {
            IntegrationManagementConfigurer configurer = new IntegrationManagementConfigurer();
            configurer.setDefaultCountsEnabled(true);
            configurer.setDefaultStatsEnabled(true);
            return configurer;
        }

        @Bean
        public SpringIntegrationMetrics springIntegrationMetrics(
            IntegrationManagementConfigurer configurer) {
            return new SpringIntegrationMetrics(configurer);
        }
    }
}
