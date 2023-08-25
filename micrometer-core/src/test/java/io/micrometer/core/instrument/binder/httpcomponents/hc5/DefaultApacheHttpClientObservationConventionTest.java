/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.KeyValue;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultApacheHttpClientObservationConvention}.
 *
 * @author Brian Clozel
 */
class DefaultApacheHttpClientObservationConventionTest {

    private final ApacheHttpClientObservationConvention observationConvention = DefaultApacheHttpClientObservationConvention.INSTANCE;

    @Test
    void shouldHaveDefaultName() {
        assertThat(observationConvention.getName()).isEqualTo("httpcomponents.httpclient.request");
    }

    @Test
    void shouldHaveContextName() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getContextualName(context)).isEqualTo("HTTP GET");
    }

    @Test
    void shouldHaveDefaultContextName() {
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(null, clientContext);
        assertThat(observationConvention.getContextualName(context)).isEqualTo("HTTP UNKNOWN");
    }

    @Test
    void shouldContributeExceptionNoneWhenSuccess() {
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(null, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("exception", KeyValue.NONE_VALUE));
    }

    @Test
    void shouldContributeExceptionWhenFailure() {
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(null, clientContext);
        context.setError(new IllegalStateException("error"));
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("exception", "IllegalStateException"));
    }

    @Test
    void shouldContributeHttpMethodName() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("method", "GET"));
    }

    @Test
    void shouldContributeDefaultHttpMethodName() {
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(null, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("method", "UNKNOWN"));
    }

    @Test
    void shouldContributeOutcome() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        context.setResponse(SimpleHttpResponse.create(200));
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("outcome", "SUCCESS"));
    }

    @Test
    void shouldContributeDefaultOutcome() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("outcome", "UNKNOWN"));
    }

    @ParameterizedTest
    @MethodSource("exceptionsSource")
    void shouldContributeStatusForErrors(Throwable exception) {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        context.setError(exception);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("status", "IO_ERROR"));
    }

    static Stream<Arguments> exceptionsSource() {
        return Stream.of(Arguments.of(new IOException()), Arguments.of(new RuntimeException()),
                Arguments.of(new HttpException()));
    }

    @Test
    void shouldContributeStatusForMissingResponse() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("status", "CLIENT_ERROR"));
    }

    @Test
    void shouldContributeStatusForResponse() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        context.setResponse(SimpleHttpResponse.create(200));
        assertThat(observationConvention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("status", "200"));
    }

    @Test
    void shouldContributeTargetWhenUnknown() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context)).contains(
                KeyValue.of("target.host", "UNKNOWN"), KeyValue.of("target.port", "UNKNOWN"),
                KeyValue.of("target.scheme", "UNKNOWN"));
    }

    @Test
    void shouldContributeTargetWhenAvailable() throws Exception {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        clientContext.setAttribute(HttpClientContext.HTTP_ROUTE,
                new HttpRoute(HttpHost.create("https://example.org:80")));
        assertThat(observationConvention.getLowCardinalityKeyValues(context)).contains(
                KeyValue.of("target.host", "example.org"), KeyValue.of("target.port", "80"),
                KeyValue.of("target.scheme", "https"));
    }

    @Test
    void shouldContributeUriTemplateWhenUnknown() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("uri", "UNKNOWN"));
    }

    @Test
    void shouldContributeUriTemplateFromAttribute() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource").build();
        HttpClientContext clientContext = HttpClientContext.create();
        clientContext.setAttribute(ApacheHttpClientObservationConvention.URI_TEMPLATE_ATTRIBUTE,
                "https://example.org/{id}");
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("uri", "https://example.org/{id}"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldContributeUriTemplateFromHeader() {
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://example.org/resource")
            .setHeader(DefaultUriMapper.URI_PATTERN_HEADER, "https://example.org/{id}")
            .build();
        HttpClientContext clientContext = HttpClientContext.create();
        ApacheHttpClientContext context = new ApacheHttpClientContext(request, clientContext);
        assertThat(observationConvention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("uri", "https://example.org/{id}"));
    }

}
