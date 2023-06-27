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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ObservationExecChainHandler}.
 *
 * @author Brian Clozel
 */
@ExtendWith(WiremockResolver.class)
class ObservationExecChainHandlerIntegrationTest {

    private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    }

    @Nested
    class ClassicClientTests {

        private static final HttpClientResponseHandler<ClassicHttpResponse> NOOP_RESPONSE_HANDLER = (
                response) -> response;

        @Test
        void retriesAreMetered_overall(@WiremockResolver.Wiremock WireMockServer server)
                throws IOException, ExecutionException, InterruptedException {

            server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));

            Instant start = Instant.now();
            try (CloseableHttpClient client = createClient(observationRegistry)) {
                execute(client, new HttpGet(server.baseUrl() + "/error"));
            }
            Duration duration = Duration.between(start, Instant.now());

            // The library performed a retry, 2 requests in total.
            server.verify(exactly(2), getRequestedFor(urlEqualTo("/error")));

            // Total duration of the library call will include the 1-second delay
            // imposed by the retry-handler.
            assertThat(duration).isCloseTo(Duration.ofMillis(1100L), Duration.ofMillis(200L));

            // How to assert the recorded time using the observationRegistry?
            Timer timer = meterRegistry.get("httpcomponents.httpclient.request")
                .tags("outcome", "SERVER_ERROR")
                .tags("exception", "none")
                .timer();

            Duration totalTime = Duration.ofMillis((long) timer.totalTime(TimeUnit.MILLISECONDS));
            assertThat(totalTime).isCloseTo(Duration.ofMillis(1100L), Duration.ofMillis(200L));

            assertThat(timer.count()).isEqualTo(1L);

            assertThat(observationRegistry)
                .hasNumberOfObservationsWithNameEqualTo("httpcomponents.httpclient.request", 1)
                // the recorded keyValues represent the final outcome.
                .hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue("outcome", "SERVER_ERROR")
                .hasLowCardinalityKeyValue("exception", "none");

        }

        private CloseableHttpClient createClient(TestObservationRegistry observationRegistry) {
            return HttpClients.custom()
                .addExecInterceptorFirst("micrometer", new ObservationExecChainHandler(observationRegistry))
                .build();
        }

        private void execute(CloseableHttpClient client, ClassicHttpRequest request) throws IOException {
            EntityUtils.consume(client.execute(request, NOOP_RESPONSE_HANDLER).getEntity());
        }

    }

    @Nested
    class AsyncClientTests {

        @Test
        void retriesAreMetered_overall(@WiremockResolver.Wiremock WireMockServer server)
                throws IOException, ExecutionException, InterruptedException {

            server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));

            Instant start = Instant.now();
            try (CloseableHttpAsyncClient client = createClient(observationRegistry)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/error").build());
            }
            Duration duration = Duration.between(start, Instant.now());

            // The library performed a retry, 2 requests in total.
            server.verify(exactly(2), getRequestedFor(urlEqualTo("/error")));

            // Total duration of the library call will include the 1-second delay
            // imposed by the retry-handler.
            assertThat(duration).isCloseTo(Duration.ofMillis(1200L), Duration.ofMillis(150L));

            // How to assert the recorded time using the observationRegistry?
            Timer timer = meterRegistry.get("httpcomponents.httpclient.request")
                .tags("outcome", "SERVER_ERROR")
                .timer();

            Duration totalTime = Duration.ofMillis((long) timer.totalTime(TimeUnit.MILLISECONDS));
            assertThat(totalTime).isCloseTo(Duration.ofMillis(1200L), Duration.ofMillis(150L));

            assertThat(timer.count()).isEqualTo(1L);

            assertThat(observationRegistry)
                // since the execChain handler is placed at the first position, we only
                // meter 1 request
                .hasNumberOfObservationsWithNameEqualTo("httpcomponents.httpclient.request", 1)
                // the recorded keyValues represent the final outcome.
                .hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue("outcome", "SERVER_ERROR")
                .hasLowCardinalityKeyValue("exception", "none");

        }

        @Test
        void testPositiveOutcomeAfterRetry_overall(@WiremockResolver.Wiremock WireMockServer server)
                throws ExecutionException, InterruptedException, IOException {
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Cause Success"));
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs("Cause Success")
                .willReturn(aResponse().withStatus(200)));
        }

        private CloseableHttpAsyncClient createClient(TestObservationRegistry observationRegistry) {
            return HttpAsyncClients.custom()
                .addExecInterceptorFirst("micrometer", new ObservationExecChainHandler(observationRegistry))
                .build();
        }

        private void execute(CloseableHttpAsyncClient client, SimpleHttpRequest request)
                throws IOException, ExecutionException, InterruptedException {
            client.start();
            Future<SimpleHttpResponse> responseFuture = client.execute(request, null);
            responseFuture.get();
        }

    }

}
