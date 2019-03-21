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
package io.micrometer.spring.autoconfigure.web.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.autoconfigure.OnlyOnceLoggingDenyMeterFilter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import io.micrometer.spring.web.client.DefaultRestTemplateExchangeTagsProvider;
import io.micrometer.spring.web.client.MetricsRestTemplateCustomizer;
import io.micrometer.spring.web.client.RestTemplateExchangeTagsProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.WebClientAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Configuration for {@link RestTemplate}- and {@link AsyncRestTemplate}-related metrics.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @author Raheela Aslam
 * @author Johnny Lim
 */
@Configuration
@AutoConfigureAfter({
    MetricsAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class,
    WebClientAutoConfiguration.class })
@ConditionalOnClass(name = {
    "org.springframework.web.client.RestTemplate",
    "org.springframework.web.client.AsyncRestTemplate",
    "org.springframework.boot.web.client.RestTemplateCustomizer" // didn't exist until Boot 1.4
})
@Conditional(RestTemplateMetricsAutoConfiguration.RestTemplateMetricsConditionalOnBeans.class)
public class RestTemplateMetricsAutoConfiguration {

    private final MetricsProperties properties;

    public RestTemplateMetricsAutoConfiguration(MetricsProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(RestTemplateExchangeTagsProvider.class)
    public DefaultRestTemplateExchangeTagsProvider restTemplateTagConfigurer() {
        return new DefaultRestTemplateExchangeTagsProvider();
    }

    @Bean
    public MetricsRestTemplateCustomizer metricsRestTemplateCustomizer(MeterRegistry meterRegistry,
                                                                       RestTemplateExchangeTagsProvider restTemplateTagConfigurer) {
        return new MetricsRestTemplateCustomizer(meterRegistry, restTemplateTagConfigurer,
            properties.getWeb().getClient().getRequestsMetricName());
    }

    @Bean
    public SmartInitializingSingleton metricsAsyncRestTemplateInitializer(final ObjectProvider<List<AsyncRestTemplate>> asyncRestTemplatesProvider,
                                                                          final MetricsRestTemplateCustomizer customizer) {
        return () -> {
            final List<AsyncRestTemplate> asyncRestTemplates = asyncRestTemplatesProvider.getIfAvailable();
            if (!CollectionUtils.isEmpty(asyncRestTemplates)) {
                asyncRestTemplates.forEach(customizer::customize);
            }
        };
    }

    @Bean
    @Order(0)
    public MeterFilter metricsHttpClientUriTagFilter() {
        String metricName = this.properties.getWeb().getClient().getRequestsMetricName();
        MeterFilter denyFilter = new OnlyOnceLoggingDenyMeterFilter(() -> String
                .format("Reached the maximum number of URI tags for '%s'. Are you using "
                        + "'uriVariables'?", metricName));
        return MeterFilter.maximumAllowableTags(metricName, "uri",
                this.properties.getWeb().getClient().getMaxUriTags(), denyFilter);
    }

    static class RestTemplateMetricsConditionalOnBeans extends AllNestedConditions {

        RestTemplateMetricsConditionalOnBeans() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(MeterRegistry.class)
        static class ConditionalOnMeterRegistryBean {
        }

        @ConditionalOnBean(RestTemplateBuilder.class)
        static class ConditionalOnRestTemplateBuilderBean {
        }

    }

}
