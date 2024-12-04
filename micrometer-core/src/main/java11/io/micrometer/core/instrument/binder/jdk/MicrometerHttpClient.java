/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jdk;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Delegates to an {@link HttpClient} while instrumenting with Micrometer any HTTP calls
 * made. Example setup: <pre>{@code
 * HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(HttpClient.newHttpClient(), meterRegistry).build();
 * }</pre>
 *
 * Inspired by <a href=
 * "https://github.com/raphw/interceptable-http-client">interceptable-http-client</a>.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the same class in the micrometer-java11 module instead
 */
@Deprecated
public class MicrometerHttpClient extends HttpClient {

    /**
     * Header name for URI pattern.
     */
    public static final String URI_PATTERN_HEADER = "URI_PATTERN";

    private final MeterRegistry meterRegistry;

    private final HttpClient client;

    @Nullable
    private final ObservationRegistry observationRegistry;

    @Nullable
    private final HttpClientObservationConvention customObservationConvention;

    private final Function<HttpRequest, String> uriMapper;

    private MicrometerHttpClient(MeterRegistry meterRegistry, HttpClient client,
            @Nullable ObservationRegistry observationRegistry,
            @Nullable HttpClientObservationConvention customObservationConvention,
            Function<HttpRequest, String> uriMapper) {
        this.meterRegistry = meterRegistry;
        this.client = client;
        this.observationRegistry = observationRegistry;
        this.customObservationConvention = customObservationConvention;
        this.uriMapper = uriMapper;
    }

    /**
     * Builder for instrumentation of {@link HttpClient}.
     * @param httpClient HttpClient to wrap
     * @param meterRegistry meter registry
     * @return builder
     */
    public static InstrumentationBuilder instrumentationBuilder(HttpClient httpClient, MeterRegistry meterRegistry) {
        return new InstrumentationBuilder(httpClient, meterRegistry);
    }

    /**
     * Builder for {@link MicrometerHttpClient}.
     */
    public static class InstrumentationBuilder {

        private final HttpClient client;

        private final MeterRegistry meterRegistry;

        @Nullable
        private ObservationRegistry observationRegistry;

        @Nullable
        private HttpClientObservationConvention customObservationConvention;

        private Function<HttpRequest, String> uriMapper = request -> request.headers()
            .firstValue(URI_PATTERN_HEADER)
            .orElse("UNKNOWN");

        /**
         * Creates a new instance of {@link InstrumentationBuilder}.
         * @param client client to wrap
         * @param meterRegistry a {@link MeterRegistry}
         */
        public InstrumentationBuilder(HttpClient client, MeterRegistry meterRegistry) {
            this.client = client;
            this.meterRegistry = meterRegistry;
        }

        /**
         * Set {@link ObservationRegistry} if you want to use {@link Observation}.
         * @param observationRegistry observation registry
         * @return this
         */
        public InstrumentationBuilder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        /**
         * When used with {@link ObservationRegistry}, it will override the default
         * {@link HttpClientObservationConvention}.
         * @param customObservationConvention custom observation convention
         * @return this
         */
        public InstrumentationBuilder customObservationConvention(
                HttpClientObservationConvention customObservationConvention) {
            this.customObservationConvention = customObservationConvention;
            return this;
        }

        /**
         * Provides custom URI mapper mechanism.
         * @param uriMapper URI mapper
         * @return this
         */
        public InstrumentationBuilder uriMapper(Function<HttpRequest, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Builds a wrapped {@link HttpClient}.
         * @return a wrapped {@link HttpClient}
         */
        public HttpClient build() {
            return new MicrometerHttpClient(this.meterRegistry, this.client, this.observationRegistry,
                    this.customObservationConvention, this.uriMapper);
        }

    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return client.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return client.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return client.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return client.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return client.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return client.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return client.authenticator();
    }

    @Override
    public Version version() {
        return client.version();
    }

    @Override
    public Optional<Executor> executor() {
        return client.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        HttpRequest.Builder httpRequestBuilder = decorate(httpRequest);
        ObservationOrTimerCompatibleInstrumentation<HttpClientContext> instrumentation = observationOrTimer(
                httpRequestBuilder);
        HttpRequest request = httpRequestBuilder.build();
        HttpResponse<T> response = null;
        try {
            response = client.send(request, bodyHandler);
            instrumentation.setResponse(response);
            return response;
        }
        catch (IOException ex) {
            instrumentation.setThrowable(ex);
            throw ex;
        }
        finally {
            stopObservationOrTimer(instrumentation, request, response);
        }
    }

    private <T> void stopObservationOrTimer(
            ObservationOrTimerCompatibleInstrumentation<HttpClientContext> instrumentation, HttpRequest request,
            @Nullable HttpResponse<T> res) {
        instrumentation.stop(DefaultHttpClientObservationConvention.INSTANCE.getName(), "Timer for JDK's HttpClient",
                () -> Tags.of(HttpClientObservationDocumentation.LowCardinalityKeys.METHOD.asString(), request.method(),
                        HttpClientObservationDocumentation.LowCardinalityKeys.URI.asString(),
                        DefaultHttpClientObservationConvention.INSTANCE.getUriTag(request, res, uriMapper),
                        HttpClientObservationDocumentation.LowCardinalityKeys.STATUS.asString(),
                        DefaultHttpClientObservationConvention.INSTANCE.getStatus(res),
                        HttpClientObservationDocumentation.LowCardinalityKeys.OUTCOME.asString(),
                        DefaultHttpClientObservationConvention.INSTANCE.getOutcome(res)));
    }

    private ObservationOrTimerCompatibleInstrumentation<HttpClientContext> observationOrTimer(
            HttpRequest.Builder httpRequestBuilder) {
        return ObservationOrTimerCompatibleInstrumentation.start(this.meterRegistry, this.observationRegistry, () -> {
            HttpClientContext context = new HttpClientContext(this.uriMapper);
            context.setCarrier(httpRequestBuilder);
            return context;
        }, this.customObservationConvention, DefaultHttpClientObservationConvention.INSTANCE);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest,
            HttpResponse.BodyHandler<T> bodyHandler) {
        return sendAsync(httpRequest, bodyHandler, null);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest,
            HttpResponse.BodyHandler<T> bodyHandler, @Nullable HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        HttpRequest.Builder httpRequestBuilder = decorate(httpRequest);
        ObservationOrTimerCompatibleInstrumentation<HttpClientContext> instrumentation = observationOrTimer(
                httpRequestBuilder);
        HttpRequest request = httpRequestBuilder.build();
        return client.sendAsync(request, bodyHandler, pushPromiseHandler).handle((response, throwable) -> {
            instrumentation.setResponse(response);
            if (throwable != null) {
                instrumentation.setThrowable(throwable);
                stopObservationOrTimer(instrumentation, request, response);
                throw new CompletionException(throwable);
            }
            else {
                stopObservationOrTimer(instrumentation, request, response);
                return response;
            }
        });
    }

    private HttpRequest.Builder decorate(HttpRequest httpRequest) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(httpRequest.uri());
        builder.expectContinue(httpRequest.expectContinue());
        httpRequest.headers().map().forEach((key, values) -> values.forEach(value -> builder.header(key, value)));
        httpRequest.bodyPublisher()
            .ifPresentOrElse(publisher -> builder.method(httpRequest.method(), publisher), () -> {
                switch (httpRequest.method()) {
                    case "GET":
                        builder.GET();
                        break;
                    case "DELETE":
                        builder.DELETE();
                        break;
                    default:
                        throw new IllegalStateException(httpRequest.method());
                }
            });
        httpRequest.timeout().ifPresent(builder::timeout);
        httpRequest.version().ifPresent(builder::version);
        return builder;
    }

}
