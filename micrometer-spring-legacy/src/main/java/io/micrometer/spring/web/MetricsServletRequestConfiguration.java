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
package io.micrometer.spring.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.MetricsConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configures instrumentation of Spring Web MVC servlet-based request mappings.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(MetricsConfigurationProperties.class)
public class MetricsServletRequestConfiguration extends WebMvcConfigurerAdapter {
    @Bean
    @ConditionalOnMissingBean(WebServletTagConfigurer.class)
    WebServletTagConfigurer webmvcTagConfigurer() {
        return new WebServletTagConfigurer();
    }

    @Bean
    ControllerMetrics controllerMetrics(MeterRegistry registry,
                                        MetricsConfigurationProperties properties,
                                        WebServletTagConfigurer configurer) {
        return new ControllerMetrics(registry, properties, configurer);
    }

    @Bean
    MetricsHandlerInterceptor webMetricsInterceptor(ControllerMetrics controllerMetrics) {
        return new MetricsHandlerInterceptor(controllerMetrics);
    }

    @Configuration
    class MetricsServletRequestInterceptorConfiguration extends WebMvcConfigurerAdapter {
        @Autowired
        MetricsHandlerInterceptor handlerInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(handlerInterceptor);
        }
    }
}