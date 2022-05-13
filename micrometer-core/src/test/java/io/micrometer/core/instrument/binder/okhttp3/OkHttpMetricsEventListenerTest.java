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
package io.micrometer.core.instrument.binder.okhttp3;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.observation.TimerObservationHandler;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link OkHttpMetricsEventListener}.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nurettin Yilmaz
 */
@ExtendWith(WiremockResolver.class)
class OkHttpMetricsEventListenerTest {

    private static final String URI_EXAMPLE_VALUE = "uriExample";

    private static final Function<Request, String> URI_MAPPER = req -> URI_EXAMPLE_VALUE;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private OkHttpClient client = new OkHttpClient.Builder().eventListener(defaultListenerBuilder().build()).build();

    private OkHttpMetricsEventListener.Builder defaultListenerBuilder() {
        return OkHttpMetricsEventListener
                .builder(registry, "okhttp.requests").tags(Tags.of("foo", "bar")).uriMapper(URI_MAPPER);
    }

    @Test
    void timeSuccessful(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder().url(server.baseUrl()).build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "status", "200", "uri", URI_EXAMPLE_VALUE, "target.host", "localhost",
                        "target.port", String.valueOf(server.port()), "target.scheme", "http")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeSuccessfulWithObservation(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        TestHandler testHandler = new TestHandler();
        observationRegistry.observationConfig().observationHandler(testHandler);
        observationRegistry.observationConfig().observationHandler(new TimerObservationHandler(registry));
        client = new OkHttpClient.Builder().eventListener(defaultListenerBuilder()
                .observationRegistry(observationRegistry)
                .build()).build();
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder().url(server.baseUrl()).build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "status", "200", "uri", URI_EXAMPLE_VALUE, "target.host", "localhost",
                        "target.port", String.valueOf(server.port()), "target.scheme", "http")
                .timer().count()).isEqualTo(1L);
        assertThat(testHandler.context).isNotNull();
        assertThat(testHandler.context.getAllKeyValues())
                .contains(KeyValue.of("foo", "bar"), KeyValue.of("status", "200"));
    }

    @Test
    void timeNotFound(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
        Request request = new Request.Builder().url(server.baseUrl()).build();

        client.newCall(request).execute().close();

        assertThat(
                registry.get("okhttp.requests")
                        .tags("foo", "bar", "status", "404", "uri", "NOT_FOUND", "target.host", "localhost",
                                "target.port", String.valueOf(server.port()), "target.scheme", "http")
                        .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeFailureDueToTimeout(@WiremockResolver.Wiremock WireMockServer server) {
        Request request = new Request.Builder().url(server.baseUrl()).build();

        server.stop();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MILLISECONDS).eventListener(defaultListenerBuilder().build())
                .build();

        try {
            client.newCall(request).execute().close();
            fail("Expected IOException.");
        }
        catch (IOException ignored) {
            // expected
        }

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", URI_EXAMPLE_VALUE, "status", "IO_ERROR", "target.host", "localhost").timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void uriTagWorksWithUriPatternHeader(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder().url(server.baseUrl() + "/helloworld.txt")
                .header(OkHttpMetricsEventListener.URI_PATTERN, "/").build();

        client = new OkHttpClient.Builder().eventListener(
                OkHttpMetricsEventListener.builder(registry, "okhttp.requests").tags(Tags.of("foo", "bar")).build())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests").tags("foo", "bar", "uri", "/", "status", "200", "target.host",
                "localhost", "target.port", String.valueOf(server.port()), "target.scheme", "http").timer().count())
                        .isEqualTo(1L);
    }

    @Test
    void uriTagWorksWithUriMapper(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .uriMapper(req -> req.url().encodedPath()).tags(Tags.of("foo", "bar")).build())
                .build();

        Request request = new Request.Builder().url(server.baseUrl() + "/helloworld.txt").build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", "/helloworld.txt", "status", "200", "target.host", "localhost",
                        "target.port", String.valueOf(server.port()), "target.scheme", "http")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void contextSpecificTags(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .tag((req, res) -> Tag.of("another.uri", req.url().encodedPath())).build())
                .build();

        Request request = new Request.Builder().url(server.baseUrl() + "/helloworld.txt").build();

        client.newCall(request).execute().close();

        assertThat(
                registry.get("okhttp.requests").tags("another.uri", "/helloworld.txt", "status", "200").timer().count())
                        .isEqualTo(1L);
    }

    @Test
    void cachedResponsesDoNotLeakMemory(@WiremockResolver.Wiremock WireMockServer server, @TempDir Path tempDir)
            throws IOException {
        OkHttpMetricsEventListener listener = OkHttpMetricsEventListener.builder(registry, "okhttp.requests").build();
        OkHttpClient clientWithCache = new OkHttpClient.Builder().eventListener(listener)
                .cache(new Cache(tempDir.toFile(), 55555)).build();
        server.stubFor(any(anyUrl()).willReturn(aResponse().withHeader("Cache-Control", "max-age=9600")));
        Request request = new Request.Builder().url(server.baseUrl()).build();

        clientWithCache.newCall(request).execute().close();
        assertThat(listener.callState).isEmpty();
        try (Response response = clientWithCache.newCall(request).execute()) {
            assertThat(response.cacheResponse()).isNotNull();
        }

        assertThat(listener.callState).isEmpty();
    }

    @Test
    void requestTagsWithClass(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        Request request = new Request.Builder().url(server.baseUrl() + "/helloworld.txt")
                .tag(Tags.class, Tags.of("requestTag1", "tagValue1")).build();

        testRequestTags(server, request);
    }

    @Test
    void requestTagsWithoutClass(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        Request request = new Request.Builder().url(server.baseUrl() + "/helloworld.txt")
                .tag(Tags.of("requestTag1", "tagValue1")).build();

        testRequestTags(server, request);
    }

    @Test
    void hostTagCanBeDisabled(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(
                        OkHttpMetricsEventListener.builder(registry, "okhttp.requests").includeHostTag(false).build())
                .build();
        Request request = new Request.Builder().url(server.baseUrl()).build();

        client.newCall(request).execute().close();

        assertThat(
                registry.get("okhttp.requests")
                        .tags("status", "200", "target.host", "localhost", "target.port", String.valueOf(server.port()),
                                "target.scheme", "http")
                        .timer().getId().getTags()).doesNotContain(Tag.of("host", "localhost"));
    }

    @Test
    void timeWhenRequestIsNull() {
        OkHttpMetricsEventListener listener = OkHttpMetricsEventListener.builder(registry, "okhttp.requests").build();
        OkHttpMetricsEventListener.CallState state = new OkHttpMetricsEventListener.CallState(
                registry.config().clock().monotonicTime(), null);
        state.setContext(new OkHttpContext(state, request -> "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false));
        listener.time(state);

        assertThat(registry.get("okhttp.requests")
                .tags("uri", "UNKNOWN", "target.host", "UNKNOWN", "target.port", "UNKNOWN", "target.scheme", "UNKNOWN")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeWhenRequestIsNullAndRequestTagKeysAreGiven() {
        OkHttpMetricsEventListener listener = OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                .requestTagKeys("tag1", "tag2").build();
        OkHttpMetricsEventListener.CallState state = new OkHttpMetricsEventListener.CallState(
                registry.config().clock().monotonicTime(), null);
        state.setContext(new OkHttpContext(state, request -> "", Collections.emptyList(), Collections.emptyList(), Arrays.asList(Tag.of("tag1", "UNKNOWN"), Tag.of("tag2", "UNKNOWN")), false));
        listener.time(state);

        assertThat(registry.get("okhttp.requests").tags("uri", "UNKNOWN", "tag1", "UNKNOWN", "tag2", "UNKNOWN").timer()
                .count()).isEqualTo(1L);
    }

    private void testRequestTags(@WiremockResolver.Wiremock WireMockServer server, Request request) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .uriMapper(req -> req.url().encodedPath()).tags(Tags.of("foo", "bar")).build())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", "/helloworld.txt", "status", "200", "requestTag1", "tagValue1",
                        "target.host", "localhost", "target.port", String.valueOf(server.port()), "target.scheme",
                        "http")
                .timer().count()).isEqualTo(1L);
    }

    static class TestHandler implements ObservationHandler<Observation.Context> {

        Observation.Context context;

        @Override
        public void onStart(Observation.Context context) {
            this.context = context;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

}
