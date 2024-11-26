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

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MicrometerHttpRequestExecutor}.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 */
@ExtendWith(WiremockResolver.class)
@SuppressWarnings("deprecation")
class MicrometerHttpRequestExecutorTest {

    private static final String EXPECTED_METER_NAME = "httpcomponents.httpclient.request";

    private static final HttpClientResponseHandler<ClassicHttpResponse> NOOP_RESPONSE_HANDLER = (response) -> response;

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void timeSuccessful(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get(EXPECTED_METER_NAME).timer().count()).isEqualTo(1L);
    }

    private void execute(CloseableHttpClient client, ClassicHttpRequest request) throws IOException {
        EntityUtils.consume(client.execute(request, NOOP_RESPONSE_HANDLER).getEntity());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpMethodIsTagged(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        execute(client, new HttpGet(server.baseUrl()));
        execute(client, new HttpGet(server.baseUrl()));
        execute(client, new HttpPost(server.baseUrl()));
        assertThat(registry.get(EXPECTED_METER_NAME).tags("method", "GET").timer().count()).isEqualTo(2L);
        assertThat(registry.get(EXPECTED_METER_NAME).tags("method", "POST").timer().count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpStatusCodeIsTagged(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200)));
        server.stubFor(any(urlEqualTo("/notfound")).willReturn(aResponse().withStatus(404)));
        server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(500)));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        execute(client, new HttpGet(server.baseUrl() + "/ok"));
        execute(client, new HttpGet(server.baseUrl() + "/ok"));
        execute(client, new HttpGet(server.baseUrl() + "/notfound"));
        execute(client, new HttpGet(server.baseUrl() + "/error"));
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("method", "GET", "status", "200", "outcome", "SUCCESS")
            .timer()
            .count()).isEqualTo(2L);
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("method", "GET", "status", "404", "outcome", "CLIENT_ERROR")
            .timer()
            .count()).isEqualTo(1L);
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("method", "GET", "status", "500", "outcome", "SERVER_ERROR")
            .timer()
            .count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uriIsUnknownByDefault(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        execute(client, new HttpGet(server.baseUrl()));
        execute(client, new HttpGet(server.baseUrl() + "/someuri"));
        execute(client, new HttpGet(server.baseUrl() + "/otheruri"));
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "UNKNOWN").timer().count()).isEqualTo(3L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uriIsReadFromHttpHeader(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        HttpGet getWithHeader = new HttpGet(server.baseUrl());
        getWithHeader.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");
        execute(client, getWithHeader);
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "/some/pattern").timer().count()).isEqualTo(1L);
        assertThatThrownBy(() -> registry.get(EXPECTED_METER_NAME).tags("uri", "UNKNOWN").timer())
            .isExactlyInstanceOf(MeterNotFoundException.class)
            .hasNoCause();
    }

    @Test
    void routeNotTaggedByDefault(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(false, false));
        execute(client, new HttpGet(server.baseUrl()));
        List<String> tagKeys = registry.get(EXPECTED_METER_NAME)
            .timer()
            .getId()
            .getTags()
            .stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        assertThat(tagKeys).doesNotContain("target.scheme", "target.host", "target.port");
        assertThat(tagKeys).contains("status", "method");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void routeTaggedIfEnabled(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        CloseableHttpClient client = client(executor(true, configureObservationRegistry));
        execute(client, new HttpGet(server.baseUrl()));
        List<String> tagKeys = registry.get(EXPECTED_METER_NAME)
            .timer()
            .getId()
            .getTags()
            .stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        assertThat(tagKeys).contains("target.scheme", "target.host", "target.port");
    }

    @Test
    void waitForContinueGetsPassedToSuper() {
        MicrometerHttpRequestExecutor requestExecutor = MicrometerHttpRequestExecutor.builder(registry)
            .waitForContinue(Timeout.ofMilliseconds(1000))
            .build();
        assertThat(requestExecutor).extracting("http1Config.waitForContinueTimeout")
            .isEqualTo(Timeout.ofMilliseconds(1000));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uriMapperWorksAsExpected(boolean configureObservationRegistry,
            @WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        MicrometerHttpRequestExecutor.Builder executorBuilder = MicrometerHttpRequestExecutor.builder(registry)
            .uriMapper(request -> request.getRequestUri());
        if (configureObservationRegistry) {
            executorBuilder.observationRegistry(createObservationRegistry());
        }
        MicrometerHttpRequestExecutor executor = executorBuilder.build();
        CloseableHttpClient client = client(executor);
        execute(client, new HttpGet(server.baseUrl()));
        execute(client, new HttpGet(server.baseUrl() + "/foo"));
        execute(client, new HttpGet(server.baseUrl() + "/bar"));
        execute(client, new HttpGet(server.baseUrl() + "/foo"));
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "/").timer().count()).isEqualTo(1L);
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "/foo").timer().count()).isEqualTo(2L);
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "/bar").timer().count()).isEqualTo(1L);
    }

    @Test
    void additionalTagsAreExposed(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        MicrometerHttpRequestExecutor executor = MicrometerHttpRequestExecutor.builder(registry)
            .tags(Tags.of("foo", "bar", "some.key", "value"))
            .exportTagsForRoute(true)
            .build();
        CloseableHttpClient client = client(executor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("foo", "bar", "some.key", "value", "target.host", "localhost")
            .timer()
            .count()).isEqualTo(1L);
    }

    @Test
    void settingNullRegistryThrowsException() {
        assertThatThrownBy(() -> MicrometerHttpRequestExecutor.builder(null).build())
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasNoCause();
    }

    @Test
    void overridingUriMapperWithNullThrowsException() {
        assertThatThrownBy(() -> MicrometerHttpRequestExecutor.builder(registry).uriMapper(null).build())
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasNoCause();
    }

    @Test
    void overrideExtraTagsDoesNotThrowAnException(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        MicrometerHttpRequestExecutor executor = MicrometerHttpRequestExecutor.builder(registry).tags(null).build();
        CloseableHttpClient client = client(executor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get(EXPECTED_METER_NAME)).isNotNull();
    }

    @Test
    void globalConventionUsedWhenCustomConventionNotConfigured(@WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        observationRegistry.observationConfig().observationConvention(new CustomGlobalApacheHttpConvention());
        MicrometerHttpRequestExecutor micrometerHttpRequestExecutor = MicrometerHttpRequestExecutor.builder(registry)
            .observationRegistry(observationRegistry)
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("custom.apache.http.client.requests")).isNotNull();
    }

    @Test
    void localConventionTakesPrecedentOverGlobalConvention(@WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        observationRegistry.observationConfig().observationConvention(new CustomGlobalApacheHttpConvention());
        MicrometerHttpRequestExecutor micrometerHttpRequestExecutor = MicrometerHttpRequestExecutor.builder(registry)
            .observationRegistry(observationRegistry)
            .observationConvention(new CustomGlobalApacheHttpConvention() {
                @Override
                public String getName() {
                    return "local." + super.getName();
                }
            })
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("local.custom.apache.http.client.requests")).isNotNull();
    }

    @Test
    void localConventionConfigured(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        MicrometerHttpRequestExecutor micrometerHttpRequestExecutor = MicrometerHttpRequestExecutor.builder(registry)
            .observationRegistry(observationRegistry)
            .observationConvention(new CustomGlobalApacheHttpConvention())
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("custom.apache.http.client.requests")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "get", "post", "custom" })
    void contextualNameContainsRequestMethod(String method, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();
        MicrometerHttpRequestExecutor micrometerHttpRequestExecutor = MicrometerHttpRequestExecutor.builder(registry)
            .observationRegistry(observationRegistry)
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        switch (method) {
            case "get":
                execute(client, new HttpGet(server.baseUrl()));
                break;

            case "post":
                execute(client, new HttpPost(server.baseUrl()));
                break;

            default:
                execute(client, new HttpUriRequestBase(method, URI.create(server.baseUrl())));
                break;
        }
        assertThat(observationRegistry).hasSingleObservationThat()
            .hasContextualNameEqualToIgnoringCase("http " + method);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpStatusCodeIsTaggedWithIoError(boolean configureObservationRegistry,
            @WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(1)));
        CloseableHttpClient client = client(executor(false, configureObservationRegistry));
        assertThatThrownBy(() -> execute(client, new HttpGet(server.baseUrl() + "/error")))
            .isInstanceOf(ClientProtocolException.class);
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("method", "GET", "status", "IO_ERROR", "outcome", "UNKNOWN")
            .timer()
            .count()).isEqualTo(1L);
    }

    static class CustomGlobalApacheHttpConvention extends DefaultApacheHttpClientObservationConvention
            implements GlobalObservationConvention<ApacheHttpClientContext> {

        @Override
        public String getName() {
            return "custom.apache.http.client.requests";
        }

    }

    private CloseableHttpClient client(HttpRequestExecutor executor) {
        return HttpClientBuilder.create().setRequestExecutor(executor).build();
    }

    private HttpRequestExecutor executor(boolean exportRoutes, boolean configureObservationRegistry) {
        MicrometerHttpRequestExecutor.Builder builder = MicrometerHttpRequestExecutor.builder(registry);
        if (configureObservationRegistry) {
            builder.observationRegistry(createObservationRegistry());
        }
        return builder.exportTagsForRoute(exportRoutes).build();
    }

    private ObservationRegistry createObservationRegistry() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        return observationRegistry;
    }

}
