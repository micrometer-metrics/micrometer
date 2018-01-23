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
package io.micrometer.spring.autoconfigure.jersey2.server;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.jersey2.server.AnnotationFinder;
import io.micrometer.jersey2.server.DefaultJerseyTagsProvider;
import io.micrometer.jersey2.server.JerseyTagsProvider;
import io.micrometer.jersey2.server.MetricsApplicationEventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jersey.ResourceConfigCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Configures instrumentation of Jersey server requests.
 *
 * @author Michael Weirauch
 */
@Configuration
@ConditionalOnClass(MetricsApplicationEventListener.class)
@ConditionalOnProperty(value = "management.metrics.jersey2.server.enabled", matchIfMissing = true)
@EnableConfigurationProperties(JerseyServerMetricsProperties.class)
public class JerseyServerMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(JerseyTagsProvider.class)
    public DefaultJerseyTagsProvider jerseyTagsProvider() {
        return new DefaultJerseyTagsProvider();
    }

    @Bean
    public ResourceConfigCustomizer jerseyResourceConfigCustomizer(MeterRegistry meterRegistry,
                                                                   JerseyServerMetricsProperties properties, JerseyTagsProvider tagsProvider) {
        return (config) -> config.register(new MetricsApplicationEventListener(meterRegistry, tagsProvider,
            properties.getRequestsMetricName(), properties.isAutoTimeRequests(),
            new AnnotationFinder() {
                @Override
                public <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
                    return AnnotationUtils.findAnnotation(annotatedElement, annotationType);
                }
            }));
    }
}
