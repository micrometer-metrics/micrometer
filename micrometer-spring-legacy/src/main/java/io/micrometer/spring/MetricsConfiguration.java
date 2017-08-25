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
package io.micrometer.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.spring.binder.SpringIntegrationMetrics;
import io.micrometer.spring.export.MetricsExporter;
import io.micrometer.spring.export.atlas.AtlasExportConfiguration;
import io.micrometer.spring.export.datadog.DatadogExportConfiguration;
import io.micrometer.spring.export.ganglia.GangliaExportConfiguration;
import io.micrometer.spring.export.graphite.GraphiteExportConfiguration;
import io.micrometer.spring.export.influx.InfluxExportConfiguration;
import io.micrometer.spring.export.jmx.JmxExportConfiguration;
import io.micrometer.spring.export.prometheus.PrometheusExportConfiguration;
import io.micrometer.spring.scheduling.MetricsSchedulingAspect;
import io.micrometer.spring.web.MetricsRestTemplateConfiguration;
import io.micrometer.spring.web.MetricsServletRequestConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.support.management.IntegrationManagementConfigurer;

import java.util.Collection;

/**
 * Metrics configuration for Spring 4/Boot 1.x
 *
 * @author Jon Schneider
 */
@Configuration
@EnableConfigurationProperties(MetricsConfigurationProperties.class)
@Import({
    RecommendedMeterBinders.class,
    MetricsServletRequestConfiguration.class,
    MetricsRestTemplateConfiguration.class,

    // supported monitoring systems
    AtlasExportConfiguration.class,
    DatadogExportConfiguration.class,
    GangliaExportConfiguration.class,
    GraphiteExportConfiguration.class,
    InfluxExportConfiguration.class,
    JmxExportConfiguration.class,
    PrometheusExportConfiguration.class
})
class MetricsConfiguration {
    @ConditionalOnMissingBean(MeterRegistry.class)
    @Bean
    public CompositeMeterRegistry compositeMeterRegistry(ObjectProvider<Collection<MetricsExporter>> exportersProvider) {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();

        if (exportersProvider.getIfAvailable() != null) {
            exportersProvider.getIfAvailable().forEach(exporter -> composite.add(exporter.registry()));
        }

        return composite;
    }

    @Configuration
    static class MeterRegistryConfigurationSupport {
        public MeterRegistryConfigurationSupport(MeterRegistry registry,
                                                 MetricsConfigurationProperties config,
                                                 ObjectProvider<Collection<MeterBinder>> binders,
                                                 ObjectProvider<Collection<MeterRegistryConfigurer>> registryConfigurers) {
            if (registryConfigurers.getIfAvailable() != null) {
                registryConfigurers.getIfAvailable().forEach(conf -> conf.configureRegistry(registry));
            }

            if (binders.getIfAvailable() != null) {
                binders.getIfAvailable().forEach(binder -> binder.bindTo(registry));
            }

            if (config.getUseGlobalRegistry()) {
                Metrics.globalRegistry.add(registry);
            }
        }
    }

    /**
     * If AOP is not enabled, scheduled interception will not work.
     */
    @Bean
    @ConditionalOnClass(name = "org.aopalliance.intercept.Joinpoint")
    @ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
    public MetricsSchedulingAspect metricsSchedulingAspect(MeterRegistry registry) {
        return new MetricsSchedulingAspect(registry);
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.integration.config.EnableIntegrationManagement")
    static class MetricsIntegrationConfiguration {

        @Bean(name = IntegrationManagementConfigurer.MANAGEMENT_CONFIGURER_NAME)
        @ConditionalOnMissingBean(value = IntegrationManagementConfigurer.class, name = IntegrationManagementConfigurer.MANAGEMENT_CONFIGURER_NAME, search = SearchStrategy.CURRENT)
        public IntegrationManagementConfigurer managementConfigurer() {
            IntegrationManagementConfigurer configurer = new IntegrationManagementConfigurer();
            configurer.setDefaultCountsEnabled(true);
            configurer.setDefaultStatsEnabled(true);
            return configurer;
        }

        @Bean
        public SpringIntegrationMetrics springIntegrationMetrics(IntegrationManagementConfigurer configurer) {
            return new SpringIntegrationMetrics(configurer);
        }
    }
}
