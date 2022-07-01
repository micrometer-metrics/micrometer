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
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for HTTP client timing instrumentation that verifies the expected metrics
 * are registered and recorded after different scenarios. Use this suite to ensure that
 * your instrumentation has the expected naming and tags. WireMock is used as an HTTP
 * server to receive real requests from an instrumented HTTP client.
 */
@WireMockTest
public abstract class HttpClientTimingInstrumentationVerificationSuite extends InstrumentationVerificationSuite {

    /**
     * A default is provided that should be preferred by new instrumentations, but
     * existing instrumentations that use a different value to maintain backwards
     * compatibility may override this method to run tests with a different name.
     * @return name of the meter timing http client requests
     */
    protected String timerName() {
        return "http.client.requests";
    }

    /**
     * Send a GET request using the instrumented HTTP client to the given path on the
     * locally running WireMock server.
     * @param wmRuntimeInfo used to get the address/port info of where to send the request
     * @param path the path portion of the URL after the host name and a forward slash
     */
    abstract void sendGetRequest(WireMockRuntimeInfo wmRuntimeInfo, String path);

    @Test
    void successful(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(anyUrl()).willReturn(ok()));

        sendGetRequest(wmRuntimeInfo, "");

        Timer timer = getRegistry().get(timerName()).tags("method", "GET", "status", "200").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void notFoundResponse(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(anyUrl()).willReturn(notFound()));

        sendGetRequest(wmRuntimeInfo, "notFound");

        Timer timer = getRegistry().get(timerName()).tags("method", "GET", "status", "404").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void badRequestResponse(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(anyUrl()).willReturn(badRequest()));

        sendGetRequest(wmRuntimeInfo, "");

        Timer timer = getRegistry().get(timerName()).tags("method", "GET", "status", "400").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

}
