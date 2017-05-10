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
package org.springframework.metrics.instrument.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Jon Schneider
 */
@Configuration
public class WebMetricsConfiguration {

    /**
     * We continue to use the deprecated WebMvcConfigurerAdapter for backwards compatibility
     * with Spring Framework 4.X.
     */
    @SuppressWarnings("deprecation")
    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    @Import(WebMetricsTagProviderConfiguration.class)
    static class WebMvcConfiguration extends org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter {
        @Bean
        WebmvcMetricsHandlerInterceptor webMetricsInterceptor() {
            return new WebmvcMetricsHandlerInterceptor();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(webMetricsInterceptor());
        }
    }

    @Configuration
    @ConditionalOnWebApplication
    static class WebMetricsTagProviderConfiguration {
        @Bean
        @ConditionalOnMissingBean(WebMetricsTagProvider.class)
        @ConditionalOnClass(name = "javax.servlet.http.HttpServletRequest")
        public WebMetricsTagProvider defaultMetricsTagProvider() {
            return new DefaultWebMetricsTagProvider();
        }

        @Bean
        @ConditionalOnMissingBean(WebMetricsTagProvider.class)
        @ConditionalOnMissingClass("javax.servlet.http.HttpServletRequest")
        public WebMetricsTagProvider emptyMetricsTagProvider() {
            return new WebMetricsTagProvider() {};
        }
    }
}
