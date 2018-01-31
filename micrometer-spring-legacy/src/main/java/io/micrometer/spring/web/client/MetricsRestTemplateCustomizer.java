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
package io.micrometer.spring.web.client;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplateHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RestTemplateCustomizer} that configures the {@link RestTemplate} to record
 * request metrics.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 */
public class MetricsRestTemplateCustomizer implements RestTemplateCustomizer {

    private final MetricsClientHttpRequestInterceptor interceptor;

    /**
     * Creates a new {@code MetricsRestTemplateInterceptor} that will record metrics using
     * the given {@code meterRegistry} with tags provided by the given
     * {@code tagProvider}.
     *
     * @param meterRegistry the meter registry
     * @param tagProvider   the tag provider
     * @param metricName    the name of the recorded metric
     */
    public MetricsRestTemplateCustomizer(MeterRegistry meterRegistry,
                                         RestTemplateExchangeTagsProvider tagProvider, String metricName) {
        this.interceptor = new MetricsClientHttpRequestInterceptor(meterRegistry, metricName, tagProvider);
    }

    @Override
    public void customize(RestTemplate restTemplate) {
        UriTemplateHandler templateHandler = restTemplate.getUriTemplateHandler();
        templateHandler = this.interceptor.createUriTemplateHandler(templateHandler);
        restTemplate.setUriTemplateHandler(templateHandler);
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(this.interceptor);
        interceptors.addAll(restTemplate.getInterceptors());
        restTemplate.setInterceptors(interceptors);
    }

    public void customize(final AsyncRestTemplate restTemplate) {
        UriTemplateHandler templateHandler = restTemplate.getUriTemplateHandler();
        templateHandler = this.interceptor.createUriTemplateHandler(templateHandler);
        restTemplate.setUriTemplateHandler(templateHandler);
        List<AsyncClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(this.interceptor);
        interceptors.addAll(restTemplate.getInterceptors());
        restTemplate.setInterceptors(interceptors);
    }
}
