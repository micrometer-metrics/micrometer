/*
 * Copyright 2021 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.boot2.reactive.samples.boot.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.actuate.metrics.web.reactive.client.DefaultWebClientExchangeTagsProvider;
import org.springframework.boot.actuate.metrics.web.reactive.client.WebClientExchangeTagsProvider;
import org.springframework.boot.actuate.metrics.web.reactive.server.DefaultWebFluxTagsProvider;
import org.springframework.boot.actuate.metrics.web.reactive.server.WebFluxTagsContributor;
import org.springframework.boot.actuate.metrics.web.reactive.server.WebFluxTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

/**
 * Uses versions of Boot code that instrument with Timer start/stop instead of passing duration.
 * This overrides WebClientMetricsConfiguration and WebFluxMetricsAutoConfiguration.
 * Exclude HttpClientMetricsAutoConfiguration to avoid WebClientMetricsConfiguration being imported.
 * Exclude WebFluxMetricsAutoConfiguration so we can provide the instrumentation for webflux server side.
 */
@Configuration
class WebFluxMetricsTemporaryConfig {

    private final MetricsProperties properties;

    WebFluxMetricsTemporaryConfig(MetricsProperties properties) {
        this.properties = properties;
    }

    @Bean
    DefaultWebFluxTagsProvider webFluxTagsProvider(ObjectProvider<WebFluxTagsContributor> contributors) {
        return new DefaultWebFluxTagsProvider(this.properties.getWeb().getServer().getRequest().isIgnoreTrailingSlash(),
                contributors.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    PocMetricsWebFilter webfluxMetrics(MeterRegistry registry, WebFluxTagsProvider tagConfigurer) {
        MetricsProperties.Web.Server.ServerRequest request = this.properties.getWeb().getServer().getRequest();
        return new PocMetricsWebFilter(registry, tagConfigurer, request.getMetricName(), request.getAutotime());
    }

    @Bean
    WebClientExchangeTagsProvider defaultWebClientExchangeTagsProvider() {
        return new DefaultWebClientExchangeTagsProvider();
    }

    @Bean
    PocMetricsWebClientCustomizer metricsWebClientCustomizer(MeterRegistry meterRegistry,
                                                             WebClientExchangeTagsProvider tagsProvider, MetricsProperties properties) {
        MetricsProperties.Web.Client.ClientRequest request = properties.getWeb().getClient().getRequest();
        return new PocMetricsWebClientCustomizer(meterRegistry, tagsProvider, request.getMetricName(),
                request.getAutotime());
    }

}
