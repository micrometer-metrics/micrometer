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

import org.aspectj.lang.JoinPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TagFormatter;
import org.springframework.boot.metrics.scheduling.MetricsSchedulingAspect;
import org.springframework.web.client.RestTemplate;

/**
 * Metrics configuration for Spring 5/Boot 2.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.server.WebFilter") // TODO got to be a better way...
@Import({
        RestTemplateMetricsConfiguration.class,
        InstrumentWebfluxRequestConfiguration.class,
        RecommendedMeterBinders.class,
        MeterBinderRegistration.class
})
class MetricsConfiguration {
    // TODO when we figure out if or how Boot 2 might be different, change this
    // https://github.com/spring-projects/spring-metrics/wiki/Enhancements-To-Spring-5.0-and-Boot-2.0

    @Bean
    @ConditionalOnMissingBean(TagFormatter.class)
    public TagFormatter tagFormatter() {
        return new TagFormatter() {
        };
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(InstrumentServletRequestConfiguration.class)
    static class WebMvcConfiguration {
    }

    /**
     * If AOP is not enabled, scheduled interception will not work.
     * FIXME this will change with https://jira.spring.io/browse/SPR-15562
     */
    @Bean
    @ConditionalOnClass({RestTemplate.class, JoinPoint.class})
    @ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
    public MetricsSchedulingAspect metricsSchedulingAspect(MeterRegistry registry) {
        return new MetricsSchedulingAspect(registry);
    }

    /**
     * If AOP is not enabled, client request interception will still work, but the URI tag
     * will always be evaluated to "none".
     * FIXME this will change with https://jira.spring.io/browse/SPR-15563
     */
    @Configuration
    @ConditionalOnClass({RestTemplate.class, JoinPoint.class})
    @ConditionalOnProperty(value = "spring.aop.enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsRestTemplateAspectConfiguration {
        @Bean
        RestTemplateUrlTemplateCapturingAspect restTemplateUrlTemplateCapturingAspect() {
            return new RestTemplateUrlTemplateCapturingAspect();
        }
    }
}
