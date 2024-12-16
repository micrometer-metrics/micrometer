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
package io.micrometer.core.instrument;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.RequestReplySenderContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test suite for HTTP client timing instrumentation that verifies the expected metrics
 * are registered and recorded after different scenarios. Use this suite to ensure that
 * your instrumentation has the expected naming and tags. A locally running server is used
 * to receive real requests from an instrumented HTTP client.
 *
 * In order to make an actual HTTP call use the
 * {@link HttpClientTimingInstrumentationVerificationTests#instrumentedClient(TestType)}
 * method that will cache the instrumented instance for a test.
 */
@WireMockTest
@Incubating(since = "1.8.8")
public abstract class HttpClientTimingInstrumentationVerificationTests<CLIENT>
        extends InstrumentationTimingVerificationTests {

    @Nullable
    private CLIENT createdClient;

    /**
     * HTTP Method to verify.
     */
    public enum HttpMethod {

        GET, POST;

    }

    /**
     * Provide your client with instrumentation required for registering metrics.
     * @return instrumented client
     */
    protected abstract CLIENT clientInstrumentedWithMetrics();

    /**
     * Provide your client with instrumentation required for registering metrics via
     * {@link ObservationRegistry}.
     * @return instrumented client or {@code null} if instrumentation with
     * {@link Observation} is not supported
     */
    @Nullable
    protected abstract CLIENT clientInstrumentedWithObservations();

    /**
     * Returns the instrumented client depending on the {@link TestType}.
     * @return instrumented client with either {@link MeterRegistry} or
     * {@link ObservationRegistry}
     */
    private CLIENT instrumentedClient(TestType testType) {
        if (this.createdClient != null) {
            return this.createdClient;
        }
        if (testType == TestType.METRICS_VIA_METER_REGISTRY) {
            this.createdClient = clientInstrumentedWithMetrics();
        }
        else {
            this.createdClient = clientInstrumentedWithObservations();
        }
        return this.createdClient;
    }

    @Override
    protected String timerName() {
        return "http.client.requests";
    }

    /**
     * Send an HTTP request using the instrumented HTTP client to the given base URL and
     * path on the locally running server. The HTTP client instrumentation must be
     * configured to tag the templated path to pass this test suite. The templated path
     * will contain path variables surrounded by curly brackets to be substituted. For
     * example, for the full templated URL {@literal http://localhost:8080/cart/{cartId}}
     * the baseUrl would be {@literal http://localhost:8080}, the templatedPath would be
     * {@literal /cart/{cartId}}. One string pathVariables argument is expected for
     * substituting the cartId path variable. The number of pathVariables arguments SHOULD
     * exactly match the number of path variables in the templatedPath.
     * @param instrumentedClient instrumented client
     * @param method http method to use to send the request
     * @param baseUrl portion of the URL before the path where to send the request
     * @param templatedPath the path portion of the URL after the baseUrl, starting with a
     * forward slash, and optionally containing path variable placeholders
     * @param pathVariables optional variables to substitute into the templatedPath
     */
    protected abstract void sendHttpRequest(CLIENT instrumentedClient, HttpMethod method, @Nullable byte[] body,
            URI baseUrl, String templatedPath, String... pathVariables);

    /**
     * Convenience method provided to substitute the template placeholders for the
     * provided path variables. The number of pathVariables argument SHOULD match the
     * number of placeholders in the templatedPath. Substitutions will be made in order.
     * @param templatedPath a URL path optionally containing placeholders in curly
     * brackets
     * @param pathVariables path variable values for which placeholders should be
     * substituted
     * @return path string with substitutions, if any, performed
     */
    protected String substitutePathVariables(String templatedPath, String... pathVariables) {
        if (pathVariables.length == 0) {
            return templatedPath;
        }
        String substituted = templatedPath;
        for (String substitution : pathVariables) {
            substituted = substituted.replaceFirst("\\{.*?}", substitution);
        }
        return substituted;
    }

    @ParameterizedTest
    @EnumSource
    void getTemplatedPathForUri(TestType testType, WireMockRuntimeInfo wmRuntimeInfo) {
        checkAndSetupTestForTestType(testType);

        stubFor(get(anyUrl()).willReturn(ok()));

        String templatedPath = "/customers/{customerId}/carts/{cartId}";
        sendHttpRequest(instrumentedClient(testType), HttpMethod.GET, null, URI.create(wmRuntimeInfo.getHttpBaseUrl()),
                templatedPath, "112", "5");

        Timer timer = getRegistry().get(timerName())
            .tags("method", "GET", "status", "200", "outcome", "SUCCESS", "uri", templatedPath)
            .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @ParameterizedTest
    @EnumSource
    @Disabled("apache/jetty http client instrumentation currently fails this test")
    void timedWhenServerIsMissing(TestType testType) throws IOException {
        checkAndSetupTestForTestType(testType);

        int unusedPort = 0;
        try (ServerSocket server = new ServerSocket(0)) {
            unusedPort = server.getLocalPort();
        }

        try {
            sendHttpRequest(instrumentedClient(testType), HttpMethod.GET, null,
                    URI.create("http://localhost:" + unusedPort), "/anything");
        }
        catch (Throwable ignore) {
        }

        Timer timer = getRegistry().get(timerName()).tags("method", "GET").timer();

        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @ParameterizedTest
    @EnumSource
    void serverException(TestType testType, WireMockRuntimeInfo wmRuntimeInfo) {
        checkAndSetupTestForTestType(testType);

        stubFor(get(anyUrl()).willReturn(serverError()));

        sendHttpRequest(instrumentedClient(testType), HttpMethod.GET, null, URI.create(wmRuntimeInfo.getHttpBaseUrl()),
                "/socks");

        Timer timer = getRegistry().get(timerName())
            .tags("method", "GET", "status", "500", "outcome", "SERVER_ERROR")
            .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @ParameterizedTest
    @EnumSource
    void clientException(TestType testType, WireMockRuntimeInfo wmRuntimeInfo) {
        checkAndSetupTestForTestType(testType);

        stubFor(post(anyUrl()).willReturn(badRequest()));

        // Some HTTP clients fail POST requests with a null body
        sendHttpRequest(instrumentedClient(testType), HttpMethod.POST, new byte[0],
                URI.create(wmRuntimeInfo.getHttpBaseUrl()), "/socks");

        Timer timer = getRegistry().get(timerName())
            .tags("method", "POST", "status", "400", "outcome", "CLIENT_ERROR")
            .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    // TODO this test doesn't need to be parameterized but the custom resolver for
    // Before/After methods doesn't like when it isn't.
    @ParameterizedTest
    @EnumSource
    void headerIsPropagatedFromContext(TestType testType, WireMockRuntimeInfo wmRuntimeInfo) {
        checkAndSetupTestForTestType(testType);

        stubFor(get(anyUrl()).willReturn(ok()));

        String templatedPath = "/fxrates/{currencypair}";
        sendHttpRequest(instrumentedClient(testType), HttpMethod.GET, null, URI.create(wmRuntimeInfo.getHttpBaseUrl()),
                templatedPath, "USDJPY");

        // Only Observation-based instrumentation deals with propagation
        // TODO documentation verification maybe shouldn't be done after each test?
        // That's why this has to be after the http request is made
        assumeTrue(testType == TestType.METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER);

        verify(getRequestedFor(urlEqualTo("/fxrates/USDJPY")).withHeader("Test-Propagation", equalTo("testValue")));
    }

    @Override
    protected TestObservationRegistry createObservationRegistryWithMetrics() {
        TestObservationRegistry observationRegistryWithMetrics = super.createObservationRegistryWithMetrics();
        observationRegistryWithMetrics.observationConfig().observationHandler(new SenderPropagationHandler<>());
        return observationRegistryWithMetrics;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static class SenderPropagationHandler<T extends RequestReplySenderContext> implements ObservationHandler<T> {

        @Override
        public void onStart(T context) {
            context.getSetter().set(context.getCarrier(), "Test-Propagation", "testValue");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof RequestReplySenderContext;
        }

    }

    private void checkAndSetupTestForTestType(TestType testType) {
        if (testType == TestType.METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER) {
            assumeTrue(clientInstrumentedWithObservations() != null,
                    "You must implement the <clientInstrumentedWithObservations> method to test your instrumentation against an ObservationRegistry");
        }
    }

}
