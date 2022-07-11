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
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for HTTP client timing instrumentation that verifies the expected metrics
 * are registered and recorded after different scenarios. Use this suite to ensure that
 * your instrumentation has the expected naming and tags. A locally running server is used
 * to receive real requests from an instrumented HTTP client.
 */
@WireMockTest
@Incubating(since = "1.9.2")
public abstract class HttpClientTimingInstrumentationVerificationTests extends InstrumentationVerificationTests {

    enum HttpMethod {

        GET, POST;

    }

    /**
     * A default is provided that should be preferred by new instrumentations. Existing
     * instrumentations that use a different value to maintain backwards compatibility may
     * override this method to run tests with a different name used in assertions.
     * @return name of the meter timing http client requests
     */
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
     * @param method http method to use to send the request
     * @param baseUrl portion of the URL before the path where to send the request
     * @param templatedPath the path portion of the URL after the baseUrl, starting with a
     * forward slash, and optionally containing path variable placeholders
     * @param pathVariables optional variables to substitute into the templatedPath
     */
    abstract void sendHttpRequest(HttpMethod method, @Nullable byte[] body, URI baseUrl, String templatedPath,
            String... pathVariables);

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

    @Test
    void getTemplatedPathForUri(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(anyUrl()).willReturn(ok()));

        String templatedPath = "/customers/{customerId}/carts/{cartId}";
        sendHttpRequest(HttpMethod.GET, null, URI.create(wmRuntimeInfo.getHttpBaseUrl()), templatedPath, "112", "5");

        Timer timer = getRegistry().get(timerName()).tags("method", "GET", "status", "200", "uri", templatedPath)
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    @Disabled("apache/jetty http client instrumentation currently fails this test")
    void timedWhenServerIsMissing() throws IOException {
        int unusedPort = 0;
        try (ServerSocket server = new ServerSocket(0)) {
            unusedPort = server.getLocalPort();
        }

        try {
            sendHttpRequest(HttpMethod.GET, null, URI.create("http://localhost:" + unusedPort), "/anything");
        }
        catch (Throwable ignore) {
        }

        Timer timer = getRegistry().get(timerName()).tags("method", "GET").timer();

        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void serverException(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(anyUrl()).willReturn(serverError()));

        sendHttpRequest(HttpMethod.GET, null, URI.create(wmRuntimeInfo.getHttpBaseUrl()), "/socks");

        Timer timer = getRegistry().get(timerName()).tags("method", "GET", "status", "500").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void clientException(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post(anyUrl()).willReturn(badRequest()));

        sendHttpRequest(HttpMethod.POST, new byte[0], URI.create(wmRuntimeInfo.getHttpBaseUrl()), "/socks");

        Timer timer = getRegistry().get(timerName()).tags("method", "POST", "status", "400").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

}
