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
package io.micrometer.spring.autoconfigure.web.servlet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.web.servlet.DefaultWebMvcTagsProvider;
import io.micrometer.spring.web.servlet.MetricsHandlerInterceptor;
import io.micrometer.spring.web.servlet.WebMvcMetrics;
import io.micrometer.spring.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configures instrumentation of Spring Web MVC servlet-based request mappings.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(MetricsProperties.class)
public class WebMvcMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebMvcTagsProvider.class)
    public DefaultWebMvcTagsProvider webmvcTagConfigurer() {
        return new DefaultWebMvcTagsProvider();
    }

    @Bean
    public WebMvcMetrics controllerMetrics(MeterRegistry registry, MetricsProperties properties, WebMvcTagsProvider configurer) {
        return new WebMvcMetrics(registry, configurer,
            properties.getWeb().getServer().getRequestsMetricName(),
            properties.getWeb().getServer().isAutoTimeRequests(),
            properties.getWeb().getServer().isRecordRequestPercentiles());
    }

    @Bean
    public MetricsHandlerInterceptor webMetricsInterceptor(WebMvcMetrics controllerMetrics) {
        return new MetricsHandlerInterceptor(controllerMetrics);
    }

    @Configuration
    public class MetricsServletRequestInterceptorConfiguration extends WebMvcConfigurerAdapter {

        private final MetricsHandlerInterceptor handlerInterceptor;

        public MetricsServletRequestInterceptorConfiguration(
            MetricsHandlerInterceptor handlerInterceptor) {
            this.handlerInterceptor = handlerInterceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(this.handlerInterceptor);
        }

    }

}
