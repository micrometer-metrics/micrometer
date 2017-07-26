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

import io.micrometer.spring.web.MetricsHandlerInterceptor;
import io.micrometer.spring.web.WebmvcTagConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TagFormatter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configures instrumentation of Spring Web MVC servlet-based request mappings.
 *
 * @author Jon Schneider
 */
@Configuration
class InstrumentServletRequestConfiguration extends WebMvcConfigurerAdapter {
    @Autowired
    MeterRegistry registry;

    @Autowired
    TagFormatter formatter;

    @Autowired(required = false)
    WebmvcTagConfigurer tagConfigurer;

    @Bean
    @ConditionalOnMissingBean(WebmvcTagConfigurer.class)
    WebmvcTagConfigurer webmvcTagConfigurer(TagFormatter tagFormatter) {
        if(tagConfigurer != null)
            return tagConfigurer;
        this.tagConfigurer = new WebmvcTagConfigurer(tagFormatter);
        return this.tagConfigurer;
    }

    @Autowired
    Environment environment;

    @Bean
    MetricsHandlerInterceptor webMetricsInterceptor() {
        return new MetricsHandlerInterceptor(registry, webmvcTagConfigurer(formatter),
                environment.getProperty("spring.metrics.web.server_requests.name", "http_server_requests"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(webMetricsInterceptor());
    }
}