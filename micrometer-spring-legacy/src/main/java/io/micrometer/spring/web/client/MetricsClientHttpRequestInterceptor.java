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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;
import org.springframework.core.NamedThreadLocal;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.util.UriTemplateHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link ClientHttpRequestInterceptor} applied via a
 * {@link MetricsRestTemplateCustomizer} to record metrics.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
class MetricsClientHttpRequestInterceptor implements ClientHttpRequestInterceptor, AsyncClientHttpRequestInterceptor {

    private static final ThreadLocal<String> urlTemplateHolder = new NamedThreadLocal<>(
            "Rest Template URL Template");

    private final MeterRegistry meterRegistry;
    private final RestTemplateExchangeTagsProvider tagProvider;
    private final String metricName;

    MetricsClientHttpRequestInterceptor(MeterRegistry meterRegistry, String metricName, RestTemplateExchangeTagsProvider tagProvider) {
        this.tagProvider = tagProvider;
        this.meterRegistry = meterRegistry;
        this.metricName = metricName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        final String urlTemplate = urlTemplateHolder.get();
        urlTemplateHolder.remove();
        final Clock clock = meterRegistry.config().clock();
        final long startTime = clock.monotonicTime();
        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            return response;
        } finally {
            getTimeBuilder(urlTemplate, request, response).register(this.meterRegistry)
                    .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public ListenableFuture<ClientHttpResponse> intercept(HttpRequest request, byte[] body,
                                                          AsyncClientHttpRequestExecution execution) throws IOException {
        final String urlTemplate = urlTemplateHolder.get();
        urlTemplateHolder.remove();
        final Clock clock = meterRegistry.config().clock();
        final long startTime = clock.monotonicTime();
        ListenableFuture<ClientHttpResponse> future;
        try {
            future = execution.executeAsync(request, body);
        } catch (IOException e) {
            getTimeBuilder(urlTemplate, request, null).register(meterRegistry)
                    .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
            throw e;
        }
        future.addCallback(new ListenableFutureCallback<ClientHttpResponse>() {
            @Override
            public void onSuccess(final ClientHttpResponse response) {
                getTimeBuilder(urlTemplate, request, response).register(meterRegistry)
                        .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
            }

            @Override
            public void onFailure(final Throwable ex) {
                getTimeBuilder(urlTemplate, request, null).register(meterRegistry)
                        .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
            }
        });
        return future;
    }

    UriTemplateHandler createUriTemplateHandler(UriTemplateHandler delegate) {
        return new UriTemplateHandler() {

            @Override
            public URI expand(String url, Map<String, ?> arguments) {
                urlTemplateHolder.set(url);
                return delegate.expand(url, arguments);
            }

            @Override
            public URI expand(String url, Object... arguments) {
                urlTemplateHolder.set(url);
                return delegate.expand(url, arguments);
            }
        };
    }

    private Timer.Builder getTimeBuilder(@Nullable String urlTemplate, HttpRequest request,
                                         @Nullable ClientHttpResponse response) {
        return Timer.builder(this.metricName)
                .tags(this.tagProvider.getTags(urlTemplate, request, response))
                .description("Timer of RestTemplate operation");
    }
}
