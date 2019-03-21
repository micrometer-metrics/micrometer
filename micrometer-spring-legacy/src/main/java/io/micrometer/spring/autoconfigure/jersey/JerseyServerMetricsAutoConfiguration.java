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
package io.micrometer.spring.autoconfigure.jersey;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.jersey2.server.AnnotationFinder;
import io.micrometer.jersey2.server.DefaultJerseyTagsProvider;
import io.micrometer.jersey2.server.JerseyTagsProvider;
import io.micrometer.jersey2.server.MetricsApplicationEventListener;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.autoconfigure.OnlyOnceLoggingDenyMeterFilter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jersey.ResourceConfigCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Jersey server instrumentation.
 *
 * @author Michael Weirauch
 * @author Michael Simons
 * @author Andy Wilkinson
 * @since 1.1.0
 */
@Configuration
@AutoConfigureAfter({ MetricsAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class })
@ConditionalOnWebApplication
@ConditionalOnClass({ ResourceConfig.class, MetricsApplicationEventListener.class })
@Conditional(JerseyServerMetricsAutoConfiguration.JerseyServerMetricsConditionalOnBeans.class)
@EnableConfigurationProperties(MetricsProperties.class)
public class JerseyServerMetricsAutoConfiguration {

    private final MetricsProperties properties;

    public JerseyServerMetricsAutoConfiguration(MetricsProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(JerseyTagsProvider.class)
    public DefaultJerseyTagsProvider jerseyTagsProvider() {
        return new DefaultJerseyTagsProvider();
    }

    @Bean
    public ResourceConfigCustomizer jerseyServerMetricsResourceConfigCustomizer(
            MeterRegistry meterRegistry, JerseyTagsProvider tagsProvider) {
        MetricsProperties.Web.Server server = this.properties.getWeb().getServer();
        return (config) ->
                config.register(new MetricsApplicationEventListener(meterRegistry,
                        tagsProvider, server.getRequestsMetricName(),
                        server.isAutoTimeRequests(), new AnnotationUtilsAnnotationFinder()));
    }

    @Bean
    @Order(0)
    public MeterFilter jerseyMetricsUriTagFilter() {
        String metricName = this.properties.getWeb().getServer().getRequestsMetricName();
        MeterFilter filter = new OnlyOnceLoggingDenyMeterFilter(() -> String
                .format("Reached the maximum number of URI tags for '%s'.", metricName));
        return MeterFilter.maximumAllowableTags(metricName, "uri",
                this.properties.getWeb().getServer().getMaxUriTags(), filter);
    }

    /**
     * An {@link AnnotationFinder} that uses {@link AnnotationUtils}.
     */
    private static class AnnotationUtilsAnnotationFinder implements AnnotationFinder {

        @Override
        public <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement,
                Class<A> annotationType) {
            return AnnotationUtils.findAnnotation(annotatedElement, annotationType);
        }

    }

    static class JerseyServerMetricsConditionalOnBeans extends AllNestedConditions {

        JerseyServerMetricsConditionalOnBeans() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(MeterRegistry.class)
        static class ConditionalOnMeterRegistryBean {
        }

        @ConditionalOnBean(ResourceConfig.class)
        static class ConditionalOnResourceConfigBean {
        }

    }

}
