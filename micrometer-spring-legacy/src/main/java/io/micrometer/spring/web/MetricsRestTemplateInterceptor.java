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

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Intercepts RestTemplate requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 */
public class MetricsRestTemplateInterceptor implements ClientHttpRequestInterceptor {
    private final MeterRegistry meterRegistry;
    private final RestTemplateTagConfigurer tagProvider;
    private final String metricName;

    public MetricsRestTemplateInterceptor(MeterRegistry meterRegistry,
                                          RestTemplateTagConfigurer tagProvider,
                                          String metricName) {
        this.tagProvider = tagProvider;
        this.meterRegistry = meterRegistry;
        this.metricName = metricName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        long startTime = System.nanoTime();

        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            return response;
        } finally {
            meterRegistry.timer(metricName, tagProvider.clientHttpRequestTags(request, response))
                    .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
