/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;
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
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MicrometerHttpRequestExecutor}.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 */
@ExtendWith(WiremockResolver.class)
@Deprecated
class MicrometerHttpRequestExecutorTest {

    private static final String EXPECTED_METER_NAME = "httpcomponents.httpclient.request";

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void timeSuccessful(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        HttpClient client = client(executor(false, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        assertThat(registry.get(EXPECTED_METER_NAME).timer().count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpMethodIsTagged(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        HttpClient client = client(executor(false, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        EntityUtils.consume(client.execute(new HttpPost(server.baseUrl())).getEntity());
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
        HttpClient client = client(executor(false, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/ok")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/ok")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/notfound")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/error")).getEntity());
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
        HttpClient client = client(executor(false, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/someuri")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/otheruri")).getEntity());
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "UNKNOWN").timer().count()).isEqualTo(3L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uriIsReadFromHttpHeader(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        HttpClient client = client(executor(false, configureObservationRegistry));
        HttpGet getWithHeader = new HttpGet(server.baseUrl());
        getWithHeader.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");
        EntityUtils.consume(client.execute(getWithHeader).getEntity());
        assertThat(registry.get(EXPECTED_METER_NAME).tags("uri", "/some/pattern").timer().count()).isEqualTo(1L);
        assertThatExceptionOfType(MeterNotFoundException.class)
            .isThrownBy(() -> registry.get(EXPECTED_METER_NAME).tags("uri", "UNKNOWN").timer());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void routeNotTaggedByDefault(boolean configureObservationRegistry, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        HttpClient client = client(executor(false, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
        HttpClient client = client(executor(true, configureObservationRegistry));
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
            .waitForContinue(1000)
            .build();
        assertThat(requestExecutor).hasFieldOrPropertyWithValue("waitForContinue", 1000);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uriMapperWorksAsExpected(boolean configureObservationRegistry,
            @WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        MicrometerHttpRequestExecutor.Builder executorBuilder = MicrometerHttpRequestExecutor.builder(registry)
            .uriMapper(request -> request.getRequestLine().getUri());
        if (configureObservationRegistry) {
            executorBuilder.observationRegistry(createObservationRegistry());
        }
        MicrometerHttpRequestExecutor executor = executorBuilder.build();
        HttpClient client = client(executor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/foo")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/bar")).getEntity());
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/foo")).getEntity());
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
        HttpClient client = client(executor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
        assertThat(registry.get(EXPECTED_METER_NAME)
            .tags("foo", "bar", "some.key", "value", "target.host", "localhost")
            .timer()
            .count()).isEqualTo(1L);
    }

    @Test
    void settingNullRegistryThrowsException() {
        assertThatIllegalArgumentException().isThrownBy(() -> MicrometerHttpRequestExecutor.builder(null).build());
    }

    @Test
    void overridingUriMapperWithNullThrowsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> MicrometerHttpRequestExecutor.builder(registry).uriMapper(null).build());
    }

    @Test
    void overrideExtraTagsDoesNotThrowAnException(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        MicrometerHttpRequestExecutor executor = MicrometerHttpRequestExecutor.builder(registry).tags(null).build();
        HttpClient client = client(executor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
        HttpClient client = client(micrometerHttpRequestExecutor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
        HttpClient client = client(micrometerHttpRequestExecutor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
        HttpClient client = client(micrometerHttpRequestExecutor);
        EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
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
        HttpClient client = client(micrometerHttpRequestExecutor);
        switch (method) {
            case "get":
                EntityUtils.consume(client.execute(new HttpGet(server.baseUrl())).getEntity());
                break;

            case "post":
                EntityUtils.consume(client.execute(new HttpPost(server.baseUrl())).getEntity());
                break;

            default:
                EntityUtils.consume(client.execute(new HttpCustomMethod(method, server.baseUrl())).getEntity());
                break;
        }
        assertThat(observationRegistry).hasSingleObservationThat()
            .hasContextualNameEqualToIgnoringCase("http " + method);
    }

    private static class HttpCustomMethod extends HttpEntityEnclosingRequestBase {

        private final String method;

        private HttpCustomMethod(String method, String uri) {
            setURI(URI.create(uri));
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }

    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpStatusCodeIsTaggedWithIoError(boolean configureObservationRegistry,
            @WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(1)));
        HttpClient client = client(executor(false, configureObservationRegistry));
        assertThatThrownBy(
                () -> EntityUtils.consume(client.execute(new HttpGet(server.baseUrl() + "/error")).getEntity()))
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

    private HttpClient client(HttpRequestExecutor executor) {
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
