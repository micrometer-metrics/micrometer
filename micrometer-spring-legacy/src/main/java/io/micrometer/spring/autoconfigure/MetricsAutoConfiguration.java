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
package io.micrometer.spring.autoconfigure;

import java.util.List;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.hystrix.HystrixMetricsBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.integration.SpringIntegrationMetrics;
import io.micrometer.spring.scheduling.ScheduledMethodMetrics;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.support.management.IntegrationManagementConfigurer;

/**
 * {@link EnableAutoConfiguration} for Micrometer-based metrics.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(Timed.class)
@EnableConfigurationProperties(MetricsProperties.class)
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        RabbitAutoConfiguration.class,
        CacheAutoConfiguration.class
})
@AutoConfigureBefore(CompositeMeterRegistryAutoConfiguration.class)
public class MetricsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    @Bean
    public static MeterRegistryPostProcessor meterRegistryPostProcessor(
            ObjectProvider<List<MeterBinder>> meterBinders,
            ObjectProvider<List<MeterFilter>> meterFilters,
            ObjectProvider<List<MeterRegistryCustomizer<?>>> meterRegistryCustomizers,
            ObjectProvider<MetricsProperties> metricsProperties) {
        return new MeterRegistryPostProcessor(meterBinders, meterFilters, meterRegistryCustomizers, metricsProperties);
    }

    @Bean
    @Order(0)
    public PropertiesMeterFilter propertiesMeterFilter(MetricsProperties properties) {
        return new PropertiesMeterFilter(properties);
    }

    @Bean
    @ConditionalOnClass(name = "com.netflix.hystrix.strategy.HystrixPlugins")
    @ConditionalOnProperty(value = "management.metrics.binders.hystrix.enabled", matchIfMissing = true)
    public HystrixMetricsBinder hystrixMetricsBinder() {
        return new HystrixMetricsBinder();
    }

    /**
     * Replaced by built-in Micrometer integration starting in Spring Integration 5.0.2.
     */
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

    @Configuration
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @ConditionalOnProperty(value = "spring.aop.auto", havingValue = "true", matchIfMissing = true)
    static class AopRequiredConfiguration {

        // If AOP is not enabled, scheduled interception will not work.
        @Bean
        @ConditionalOnProperty(value = "management.metrics.binders.scheduled.enabled", matchIfMissing = true)
        @ConditionalOnMissingBean
        public ScheduledMethodMetrics metricsSchedulingAspect(MeterRegistry registry) {
            return new ScheduledMethodMetrics(registry);
        }

    }

}
