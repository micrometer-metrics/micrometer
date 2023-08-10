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
package io.micrometer.core.instrument.binder.http;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

@WireMockTest
class ObservationHttpJakartaClientFilterTests {

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new HeaderAddingHandler());
    }

    @Test
    void clientFilterShouldWorkWithJakartaHttpClient(WireMockRuntimeInfo wmRuntimeInfo) {
        wmRuntimeInfo.getWireMock().register(WireMock.get("/foo").willReturn(WireMock.aResponse().withStatus(200)));

        try (Client client = ClientBuilder.newClient()) {
            client.register(new ObservationHttpJakartaClientFilter(observationRegistry, null));
            final WebTarget target = client.target("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/foo");
            try (Response response = target.request().get()) {
                then(response.getStatus()).isEqualTo(200);
            }
        }

        wmRuntimeInfo.getWireMock()
            .verifyThat(
                    WireMock.getRequestedFor(WireMock.urlEqualTo("/foo")).withHeader("foo", WireMock.equalTo("bar")));
    }

    static class HeaderAddingHandler implements ObservationHandler<HttpJakartaClientRequestObservationContext> {

        @Override
        public void onStart(HttpJakartaClientRequestObservationContext context) {
            context.getSetter().set(context.getCarrier(), "foo", "bar");
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof HttpJakartaClientRequestObservationContext;
        }

    }

}
