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
package io.micrometer.spring.autoconfigure.web.client;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.lang.NonNull;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.web.client.DefaultRestTemplateExchangeTagsProvider;
import io.micrometer.spring.web.client.MetricsRestTemplateCustomizer;
import io.micrometer.spring.web.client.RestTemplateExchangeTagsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration for {@link RestTemplate}-related metrics.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
@Configuration
@ConditionalOnClass(name = {
    "org.springframework.web.client.RestTemplate",
    "org.springframework.boot.web.client.RestTemplateCustomizer" // didn't exist until Boot 1.4
})
public class RestTemplateMetricsConfiguration {
    private final Logger logger = LoggerFactory.getLogger(RestTemplateMetricsConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(RestTemplateExchangeTagsProvider.class)
    public DefaultRestTemplateExchangeTagsProvider restTemplateTagConfigurer() {
        return new DefaultRestTemplateExchangeTagsProvider();
    }

    @Bean
    public MetricsRestTemplateCustomizer metricsRestTemplateCustomizer(MeterRegistry meterRegistry,
                                                                       RestTemplateExchangeTagsProvider restTemplateTagConfigurer,
                                                                       MetricsProperties properties) {
        return new MetricsRestTemplateCustomizer(meterRegistry, restTemplateTagConfigurer,
            properties.getWeb().getClient().getRequestsMetricName());
    }

    @Bean
    @Order(0)
    public MeterRegistryCustomizer limitCardinalityOfUriTag(MetricsProperties properties) {
        String metricName = properties.getWeb().getClient().getRequestsMetricName();
        return r -> r.config().meterFilter(MeterFilter.maximumAllowableTags(metricName,
            "uri", properties.getWeb().getClient().getMaxUriTags(), new MeterFilter() {
                private AtomicBoolean alreadyWarned = new AtomicBoolean(false);

                @Override
                @NonNull
                public MeterFilterReply accept(@NonNull Meter.Id id) {
                    if (alreadyWarned.compareAndSet(false, true)) {
                        logger.warn("Reached the maximum number of URI tags for '" + metricName + "'. Are you using uriVariables on RestTemplate calls?");
                    }
                    return MeterFilterReply.DENY;
                }
            })
        );
    }
}
