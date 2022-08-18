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
import java.util.concurrent.Executor;

/**
 * Observed version of a {@link HttpClient}.
 *
 * Inspired by <a href=
 * "https://github.com/raphw/interceptable-http-client">interceptable-http-client</a> .
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class ObservedHttpClient extends HttpClient {

    private static final HttpClientObservationConvention DEFAULT_CONVENTION = new DefaultHttpClientObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final HttpClient client;

    @Nullable
    private final HttpClientObservationConvention customObservationConvention;

    public ObservedHttpClient(ObservationRegistry observationRegistry, HttpClient client,
            HttpClientObservationConvention customObservationConvention) {
        this.observationRegistry = observationRegistry;
        this.client = client;
        this.customObservationConvention = customObservationConvention;
    }

    public ObservedHttpClient(ObservationRegistry observationRegistry, HttpClient client) {
        this.observationRegistry = observationRegistry;
        this.client = client;
        this.customObservationConvention = null;
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
        HttpClientContext context = new HttpClientContext();
        context.setCarrier(httpRequestBuilder);
        Observation observation = HttpClientDocumentedObservation.HTTP_CALL.start(customObservationConvention,
                DEFAULT_CONVENTION, context, observationRegistry);
        HttpRequest request = httpRequestBuilder.build();
        try {
            HttpResponse<T> response = client.send(request, bodyHandler);
            context.setResponse(response);
            return response;
        }
        catch (IOException ex) {
            observation.error(ex);
            throw ex;
        }
        finally {
            observation.stop();
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest,
            HttpResponse.BodyHandler<T> bodyHandler) {
        return sendAsync(httpRequest, bodyHandler, null);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest,
            HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        HttpRequest.Builder httpRequestBuilder = decorate(httpRequest);
        HttpClientContext context = new HttpClientContext();
        context.setCarrier(httpRequestBuilder);
        Observation observation = HttpClientDocumentedObservation.HTTP_CALL.start(customObservationConvention,
                DEFAULT_CONVENTION, context, observationRegistry);
        HttpRequest request = httpRequestBuilder.build();
        return client.sendAsync(request, bodyHandler, pushPromiseHandler).handle((response, throwable) -> {
            if (throwable != null) {
                observation.error(throwable);
            }
            context.setResponse(response);
            observation.stop();
            return response;
        });
    }

    private HttpRequest.Builder decorate(HttpRequest httpRequest) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(httpRequest.uri());
        builder.expectContinue(httpRequest.expectContinue());
        httpRequest.headers().map().forEach((key, values) -> values.forEach(value -> builder.header(key, value)));
        httpRequest.bodyPublisher().ifPresentOrElse(publisher -> builder.method(httpRequest.method(), publisher),
                () -> {
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
