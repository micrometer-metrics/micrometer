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
package io.micrometer.jakarta9.instrument.binder.http.jaxrs.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
class ObservationJaxRsHttpClientFilterTests {

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new HeaderAddingHandler());
    }

    @Test
    void clientFilterShouldWorkWithJakartaHttpClient(WireMockRuntimeInfo wmRuntimeInfo) {
        wmRuntimeInfo.getWireMock().register(WireMock.get("/foo").willReturn(WireMock.aResponse().withStatus(200)));

        try (Client client = ClientBuilder.newClient()) {
            client.register(new ObservationJaxRsHttpClientFilter(observationRegistry, null));
            final WebTarget target = client.target("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/foo");
            try (Response response = target.request().get()) {
                BDDAssertions.then(response.getStatus()).isEqualTo(200);
            }
        }

        wmRuntimeInfo.getWireMock()
            .verifyThat(
                    WireMock.getRequestedFor(WireMock.urlEqualTo("/foo")).withHeader("foo", WireMock.equalTo("bar")));
    }

    @Test
    void clientFilterShouldWorkWithJakartaHttpClientForExceptions(WireMockRuntimeInfo wmRuntimeInfo) {
        wmRuntimeInfo.getWireMock()
            .register(WireMock.get("/nonexistanturl").willReturn(WireMock.aResponse().withStatus(404)));

        try (Client client = ClientBuilder.newClient()) {
            client.register(new ObservationJaxRsHttpClientFilter(observationRegistry, null));
            final WebTarget target = client
                .target("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/nonexistanturl");
            try (Response response = target.request().get()) {
                BDDAssertions.then(response.getStatus()).isEqualTo(404);
            }
        }

        wmRuntimeInfo.getWireMock()
            .verifyThat(WireMock.getRequestedFor(WireMock.urlEqualTo("/nonexistanturl"))
                .withHeader("foo", WireMock.equalTo("bar")));

        TestObservationRegistryAssert.then(observationRegistry)
            .hasSingleObservationThat()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasError();
    }

    @Test
    void clientFilterShouldWorkWithJakartaHttpClientForExceptionsWithoutResponse(WireMockRuntimeInfo wmRuntimeInfo) {
        wmRuntimeInfo.getWireMock()
            .register(WireMock.get("/connectionReset")
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        try (Client client = ClientBuilder.newClient()) {
            client.register(new ObservationJaxRsHttpClientFilter(observationRegistry, null));
            final WebTarget target = client
                .target("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/connectionReset");
            Exception exception = null;
            try (Response response = InvocationProxy.wrap(target.request(), observationRegistry).get()) {
                BDDAssertions.fail("Response should not be returned");
            }
            catch (Exception e) {
                exception = e;
            }
            BDDAssertions.then(exception).isNotNull().isInstanceOf(ProcessingException.class);
        }

        wmRuntimeInfo.getWireMock()
            .verifyThat(WireMock.getRequestedFor(WireMock.urlEqualTo("/connectionReset"))
                .withHeader("foo", WireMock.equalTo("bar")));

        TestObservationRegistryAssert.then(observationRegistry)
            .hasSingleObservationThat()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasError();
    }

    static class HeaderAddingHandler implements ObservationHandler<JaxRsHttpClientObservationContext> {

        @Override
        public void onStart(JaxRsHttpClientObservationContext context) {
            context.getSetter().set(context.getCarrier(), "foo", "bar");
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof JaxRsHttpClientObservationContext;
        }

    }

}
