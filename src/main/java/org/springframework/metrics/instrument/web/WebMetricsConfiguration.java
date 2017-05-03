/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.metrics.instrument.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * @author Jon Schneider
 */
@Configuration
public class WebMetricsConfiguration {

    /**
     * We continue to use the deprecated WebMvcConfigurerAdapter for backwards compatibility
     * with Spring Framework 4.X.
     */
    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class WebMvcConfiguration extends WebMvcConfigurerAdapter {
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
            return new EmptyWebMetricsTagProvider();
        }
    }

//	@Configuration
//	@ConditionalOnClass({ RestTemplate.class, JoinPoint.class })
//	@ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
//	static class MetricsRestTemplateAspectConfiguration {
//
//		@Bean
//		RestTemplateUrlTemplateCapturingAspect restTemplateUrlTemplateCapturingAspect() {
//			return new RestTemplateUrlTemplateCapturingAspect();
//		}
//
//	}

//	@Configuration
//	@ConditionalOnClass({ RestTemplate.class, HttpServletRequest.class })	// HttpServletRequest implicitly required by WebMetricsTagProvider
//	static class MetricsRestTemplateConfiguration {
//
//		@Value("${netflix.metrics.restClient.metricName:restclient}")
//		String metricName;
//
//		@Bean
//		MetricsClientHttpRequestInterceptor spectatorLoggingClientHttpRequestInterceptor(
//				Collection<WebMetricsTagProvider> tagProviders,
//				ServoMonitorCache servoMonitorCache) {
//			return new MetricsClientHttpRequestInterceptor(tagProviders,
//					servoMonitorCache, this.metricName);
//		}
//
//		@Bean
//		BeanPostProcessor spectatorRestTemplateInterceptorPostProcessor() {
//			return new MetricsInterceptorPostProcessor();
//		}
//
//		private static class MetricsInterceptorPostProcessor
//				implements BeanPostProcessor, ApplicationContextAware {
//			private ApplicationContext context;
//			private MetricsClientHttpRequestInterceptor interceptor;
//
//			@Override
//			public Object postProcessBeforeInitialization(Object bean, String beanName) {
//				return bean;
//			}
//
//			@Override
//			public Object postProcessAfterInitialization(Object bean, String beanName) {
//				if (bean instanceof RestTemplate) {
//					if (this.interceptor == null) {
//						this.interceptor = this.context
//								.getBean(MetricsClientHttpRequestInterceptor.class);
//					}
//					RestTemplate restTemplate = (RestTemplate) bean;
//					// create a new list as the old one may be unmodifiable (ie Arrays.asList())
//					ArrayList<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
//					interceptors.add(interceptor);
//					interceptors.addAll(restTemplate.getInterceptors());
//					restTemplate.setInterceptors(interceptors);
//				}
//				return bean;
//			}
//
//			@Override
//			public void setApplicationContext(ApplicationContext context)
//					throws BeansException {
//				this.context = context;
//			}
//		}
//	}
}
