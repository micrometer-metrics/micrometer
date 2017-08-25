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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.client.RestTemplate")
public class MetricsRestTemplateConfiguration {
    @Bean
    @ConditionalOnMissingBean(RestTemplateTagConfigurer.class)
    RestTemplateTagConfigurer restTemplateTagConfigurer() {
        return new RestTemplateTagConfigurer();
    }

    @Bean
    MetricsRestTemplateInterceptor clientHttpRequestInterceptor(MeterRegistry meterRegistry,
                                                                RestTemplateTagConfigurer restTemplateTagConfigurer,
                                                                Environment environment) {
        return new MetricsRestTemplateInterceptor(meterRegistry, restTemplateTagConfigurer,
                environment.getProperty("spring.metrics.web.client_requests.name", "http.client.requests"));
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

    /**
     * If AOP is not enabled, client request interception will still work, but the URI tag
     * will always be evaluated to "none".
     */
    @Configuration
    @ConditionalOnClass(name = {"org.aopalliance.intercept.Joinpoint"})
    @ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsRestTemplateAspectConfiguration {
        @Bean
        RestTemplateUrlTemplateCapturingAspect restTemplateUrlTemplateCapturingAspect() {
            return new RestTemplateUrlTemplateCapturingAspect();
        }
    }
}