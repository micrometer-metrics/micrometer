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
import com.github.tomakehurst.wiremock.client.WireMock;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.Propagator;
import io.micrometer.observation.transport.RequestReplySenderContext;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OkHttpObservationInterceptor}.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nurettin Yilmaz
 */
@ExtendWith(WiremockResolver.class)
class OkHttpObservationInterceptorTest {

    private static final String URI_EXAMPLE_VALUE = "uriExample";

    private static final Function<Request, String> URI_MAPPER = req -> URI_EXAMPLE_VALUE;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    private TestHandler testHandler = new TestHandler();

    // tag::setup[]
    private OkHttpClient client = new OkHttpClient.Builder().addInterceptor(defaultInterceptorBuilder().build())
        .build();

    private OkHttpObservationInterceptor.Builder defaultInterceptorBuilder() {
        return OkHttpObservationInterceptor.builder(observationRegistry, "okhttp.requests")
            .tags(KeyValues.of("foo", "bar"))
            .uriMapper(URI_MAPPER);
    }
    // end::setup[]

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(testHandler);
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        observationRegistry.observationConfig().observationHandler(new PropagatingHandler());
    }

    @Test
    void timeSuccessfulWithDefaultObservation(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        client = new OkHttpClient.Builder().addInterceptor(defaultInterceptorBuilder().build()).build();
        server.stubFor(any(anyUrl()));
        // tag::example[]
        Request request = new Request.Builder().url(server.baseUrl()).build();

        makeACall(client, request);

        assertThat(registry.get("okhttp.requests")
            .tags("foo", "bar", "status", "200", "uri", URI_EXAMPLE_VALUE, "target.host", "localhost", "target.port",
                    String.valueOf(server.port()), "target.scheme", "http")
            .timer()
            .count()).isEqualTo(1L);
        assertThat(testHandler.context).isNotNull();
        assertThat(testHandler.context.getAllKeyValues()).contains(KeyValue.of("foo", "bar"),
                KeyValue.of("status", "200"));
        // end::example[]
        server.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/")).withHeader("foo", WireMock.equalTo("bar")));
    }

    @Test
    void timeSuccessfulWithObservationConvention(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        // tag::custom_convention[]
        MyConvention myConvention = new MyConvention();
        client = new OkHttpClient.Builder()
            .addInterceptor(defaultInterceptorBuilder()
                .observationConvention(new StandardizedOkHttpObservationConvention(myConvention))
                .build())
            .build();
        // end::custom_convention[]
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder().url(server.baseUrl()).build();

        makeACall(client, request);

        assertThat(registry.get("new.name").tags("peer", "name").timer().count()).isEqualTo(1L);
        server.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/")).withHeader("foo", WireMock.equalTo("bar")));
    }

    void makeACall(OkHttpClient client, Request request) throws IOException {
        client.newCall(request).execute().close();
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

    static class PropagatingHandler implements ObservationHandler<RequestReplySenderContext<Object, Object>> {

        @Override
        public void onStart(RequestReplySenderContext<Object, Object> context) {
            Object carrier = context.getCarrier();
            Propagator.Setter<Object> setter = context.getSetter();
            setter.set(carrier, "foo", "bar");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestReplySenderContext;
        }

    }

    static class StandardizedOkHttpObservationConvention implements OkHttpObservationConvention {

        private final HttpClientKeyValuesConvention<Request, Response> convention;

        StandardizedOkHttpObservationConvention(HttpClientKeyValuesConvention<Request, Response> convention) {
            this.convention = convention;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(OkHttpContext context) {
            return KeyValues.of(convention.peerName(context.getState().request));
        }

        @Override
        public String getName() {
            return "new.name";
        }

    }

    static class MyConvention implements HttpClientKeyValuesConvention<Request, Response> {

        @Override
        public KeyValue peerName(Request request) {
            return KeyValue.of("peer", "name");
        }

        @Override
        public KeyValue method(Request request) {
            return null;
        }

        @Override
        public KeyValue url(Request request) {
            return null;
        }

        @Override
        public KeyValue target(Request request) {
            return null;
        }

        @Override
        public KeyValue host(Request request) {
            return null;
        }

        @Override
        public KeyValue scheme(Request request) {
            return null;
        }

        @Override
        public KeyValue statusCode(Response response) {
            return null;
        }

        @Override
        public KeyValue flavor(Request request) {
            return null;
        }

        @Override
        public KeyValue userAgent(Request request) {
            return null;
        }

        @Override
        public KeyValue requestContentLength(Request request) {
            return null;
        }

        @Override
        public KeyValue responseContentLength(Response response) {
            return null;
        }

        @Override
        public KeyValue ip(Request request) {
            return null;
        }

        @Override
        public KeyValue port(Request request) {
            return null;
        }

    }

}
