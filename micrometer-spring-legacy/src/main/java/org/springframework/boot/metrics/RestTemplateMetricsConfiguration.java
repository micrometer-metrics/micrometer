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
package org.springframework.boot.metrics;

import org.springframework.boot.metrics.web.MetricsRestTemplateInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TagFormatter;
import org.springframework.boot.metrics.web.RestTemplateTagConfigurer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass(RestTemplate.class)
class RestTemplateMetricsConfiguration {
    @Autowired(required = false)
    RestTemplateTagConfigurer tagConfigurer;

    @Bean
    @ConditionalOnMissingBean(RestTemplateTagConfigurer.class)
    RestTemplateTagConfigurer restTemplateTagConfigurer(TagFormatter tagFormatter) {
        if(tagConfigurer != null)
            return tagConfigurer;
        this.tagConfigurer = new RestTemplateTagConfigurer(tagFormatter);
        return tagConfigurer;
    }

    @Bean
    MetricsRestTemplateInterceptor clientHttpRequestInterceptor(MeterRegistry meterRegistry,
                                                                TagFormatter tagFormatter,
                                                                Environment environment) {
        return new MetricsRestTemplateInterceptor(meterRegistry, restTemplateTagConfigurer(tagFormatter),
                environment.getProperty("spring.metrics.web.client_requests.name", "http_client_requests"));
    }

    @Bean
    static BeanPostProcessor restTemplateInterceptorPostProcessor() {
        return new MetricsInterceptorPostProcessor();
    }

    private static class MetricsInterceptorPostProcessor
            implements BeanPostProcessor, ApplicationContextAware {
        private ApplicationContext context;
        private MetricsRestTemplateInterceptor interceptor;

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof RestTemplate) {
                if (this.interceptor == null) {
                    this.interceptor = this.context
                            .getBean(MetricsRestTemplateInterceptor.class);
                }
                RestTemplate restTemplate = (RestTemplate) bean;
                // create a new list as the old one may be unmodifiable (ie Arrays.asList())
                List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                interceptors.add(interceptor);
                interceptors.addAll(restTemplate.getInterceptors());
                restTemplate.setInterceptors(interceptors);
            }
            return bean;
        }

        @Override
        public void setApplicationContext(ApplicationContext context) throws BeansException {
            this.context = context;
        }
    }
}