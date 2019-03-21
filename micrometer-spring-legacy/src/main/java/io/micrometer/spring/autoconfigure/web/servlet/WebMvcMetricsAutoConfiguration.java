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
package io.micrometer.spring.autoconfigure.web.servlet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.autoconfigure.OnlyOnceLoggingDenyMeterFilter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import io.micrometer.spring.web.servlet.DefaultWebMvcTagsProvider;
import io.micrometer.spring.web.servlet.WebMvcMetricsFilter;
import io.micrometer.spring.web.servlet.WebMvcTagsProvider;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for instrumentation of Spring Web
 * MVC servlet-based request mappings.
 *
 * @author Jon Schneider
 * @author Dmytro Nosan
 */
@Configuration
@AutoConfigureAfter({ MetricsAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class })
@ConditionalOnWebApplication
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnBean(MeterRegistry.class)
@EnableConfigurationProperties(MetricsProperties.class)
public class WebMvcMetricsAutoConfiguration {

    private final MetricsProperties properties;

    public WebMvcMetricsAutoConfiguration(MetricsProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(WebMvcTagsProvider.class)
    public DefaultWebMvcTagsProvider servletTagsProvider() {
        return new DefaultWebMvcTagsProvider();
    }

    @SuppressWarnings("deprecation")
    @Bean
    public WebMvcMetricsFilter webMetricsFilter(MeterRegistry registry,
                                                WebMvcTagsProvider tagsProvider,
                                                WebApplicationContext ctx) {
        return new WebMvcMetricsFilter(registry, tagsProvider,
                properties.getWeb().getServer().getRequestsMetricName(),
                properties.getWeb().getServer().isAutoTimeRequests(),
                new HandlerMappingIntrospector(ctx));
    }

    @Bean
    @Order(0)
    public MeterFilter metricsHttpServerUriTagFilter() {
        String metricName = this.properties.getWeb().getServer().getRequestsMetricName();
        MeterFilter filter = new OnlyOnceLoggingDenyMeterFilter(() -> String
                .format("Reached the maximum number of URI tags for '%s'.", metricName));
        return MeterFilter.maximumAllowableTags(metricName, "uri",
                this.properties.getWeb().getServer().getMaxUriTags(), filter);
    }

}
