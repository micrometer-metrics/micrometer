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
package io.micrometer.spring.autoconfigure.web.servlet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.web.servlet.DefaultWebMvcTagsProvider;
import io.micrometer.spring.web.servlet.WebMvcMetricsFilter;
import io.micrometer.spring.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/**
 * Configures instrumentation of Spring Web MVC servlet-based request mappings.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(MetricsProperties.class)
public class ServletMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebMvcTagsProvider.class)
    public DefaultWebMvcTagsProvider servletTagsProvider() {
        return new DefaultWebMvcTagsProvider();
    }

    @SuppressWarnings("deprecation")
    @Bean
    public WebMvcMetricsFilter webMetricsFilter(MeterRegistry registry, MetricsProperties properties,
                                                WebMvcTagsProvider tagsProvider,
                                                WebApplicationContext ctx) {
        return new WebMvcMetricsFilter(registry, tagsProvider,
                properties.getWeb().getServer().getRequestsMetricName(),
                properties.getWeb().getServer().isAutoTimeRequests(),
                new HandlerMappingIntrospector(ctx));
    }
}
