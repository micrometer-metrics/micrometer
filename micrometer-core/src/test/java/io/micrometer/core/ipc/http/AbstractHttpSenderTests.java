/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.ipc.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(WiremockResolver.class)
abstract class AbstractHttpSenderTests {
    HttpSender httpSender;

    abstract void setHttpSender();

    @BeforeEach
    void setup() {
        setHttpSender();
    }

    @ParameterizedTest
    @EnumSource(HttpSender.Method.class)
    void requestSentWithDefaults(HttpSender.Method method, @Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")));

        httpSender.newRequest(server.baseUrl() + "/metrics")
                .withMethod(method)
                .withBasicAuthentication("user", "pass")
                .accept("customAccept")
                .withHeader("customHeader", "customHeaderValue")
                .send();

        server.verify(WireMock.requestMadeFor(request ->
                MatchResult.aggregate(
                        MatchResult.of(request.getMethod().getName().equals(method.name())),
                        MatchResult.of(request.getUrl().equals("/metrics"))
                ))
                .withBasicAuth(new BasicCredentials("user", "pass"))
                .withHeader("Accept", equalTo("customAccept"))
                .withHeader("customHeader", equalTo("customHeaderValue")));
    }

    @ParameterizedTest
    @EnumSource(HttpSender.Method.class)
    void errorResponseReceived(HttpSender.Method method, @Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics"))
                .willReturn(badRequest().withBody("Error processing metrics")));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics")
                .withMethod(method)
                .withBasicAuthentication("user", "pass")
                .accept("customAccept")
                .withHeader("customHeader", "customHeaderValue")
                .send();

        assertThat(response.code()).isEqualTo(400);
        if (!HttpSender.Method.HEAD.equals(method)) { // HEAD responses do not have a body
            assertThat(response.body()).isEqualTo("Error processing metrics");
        }
    }
}
