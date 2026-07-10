/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.ipc.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.lanwen.wiremock.ext.WiremockResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A compatibility kit to verify {@link HttpSender} implementations.
 *
 * @author Jon Schneider
 * @author Tommy Ludwig
 */
@ExtendWith(WiremockResolver.class)
public abstract class HttpSenderCompatibilityKit {

    protected HttpSender httpSender;

    public abstract HttpSender httpClient();

    @BeforeEach
    void setup() {
        httpSender = httpClient();
    }

    @Test
    @DisplayName("compatibility test provides a non-null http client instance")
    void httpSenderIsNotNull() {
        assertThat(httpSender).isNotNull();
    }

    @ParameterizedTest
    @DisplayName("successfully send a request with NO body and receive a response with NO body")
    @EnumSource
    void successfulRequestSentWithNoBody(HttpSender.Method method, @WiremockResolver.Wiremock WireMockServer server)
            throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics").withMethod(method).send();

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);

        server.verify(WireMock.requestMadeFor(
                request -> MatchResult.aggregate(MatchResult.of(request.getMethod().getName().equals(method.name())),
                        MatchResult.of(request.getUrl().equals("/metrics")))));
    }

    @ParameterizedTest
    @DisplayName("successfully send a request with a body and receive a response with a body")
    @EnumSource(value = HttpSender.Method.class, names = { "POST", "PUT" })
    void successfulRequestSentWithBody(HttpSender.Method method, @WiremockResolver.Wiremock WireMockServer server)
            throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(ok("a body")));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics")
            .withMethod(method)
            .accept("customAccept")
            .withContent("custom/type", "this is a line")
            .send();

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("a body");

        server.verify(WireMock
            .requestMadeFor(request -> MatchResult.aggregate(
                    MatchResult.of(request.getMethod().getName().equals(method.name())),
                    MatchResult.of(request.getUrl().equals("/metrics"))))
            .withHeader("Accept", equalTo("customAccept"))
            .withHeader("Content-Type", containing("custom/type")) // charset
                                                                   // may
                                                                   // be
                                                                   // added
                                                                   // to
                                                                   // the
                                                                   // type
            .withRequestBody(equalTo("this is a line")));
    }

    @ParameterizedTest
    @DisplayName("receive an error response")
    @EnumSource
    void errorResponseReceived(HttpSender.Method method, @WiremockResolver.Wiremock WireMockServer server)
            throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(badRequest().withBody("Error processing metrics")));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics").withMethod(method).send();

        assertThat(response.code()).isEqualTo(400);
        if (!HttpSender.Method.HEAD.equals(method)) { // HEAD responses do not have a body
            assertThat(response.body()).isEqualTo("Error processing metrics");
        }
        else {
            assertThat(response.body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);
        }
    }

    @ParameterizedTest
    @EnumSource
    void basicAuth(HttpSender.Method method, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(unauthorized()));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics")
            .withMethod(method)
            .withBasicAuthentication("superuser", "superpassword")
            .send();

        assertThat(response.code()).isEqualTo(401);

        server.verify(WireMock
            .requestMadeFor(request -> MatchResult.aggregate(
                    MatchResult.of(request.getMethod().getName().equals(method.name())),
                    MatchResult.of(request.getUrl().equals("/metrics"))))
            .withBasicAuth(new BasicCredentials("superuser", "superpassword")));
    }

    @ParameterizedTest
    @EnumSource
    void customHeader(HttpSender.Method method, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(unauthorized()));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics")
            .withMethod(method)
            .withHeader("customHeader", "customHeaderValue")
            .send();

        assertThat(response.code()).isEqualTo(401);

        server.verify(WireMock
            .requestMadeFor(request -> MatchResult.aggregate(
                    MatchResult.of(request.getMethod().getName().equals(method.name())),
                    MatchResult.of(request.getUrl().equals("/metrics"))))
            .withHeader("customHeader", equalTo("customHeaderValue")));
    }

    @Test
    void authenticationHeader(@WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(unauthorized()));

        HttpSender.Response response = httpSender.newRequest(server.baseUrl() + "/metrics")
            .withMethod(HttpSender.Method.POST)
            .withAuthentication("Bearer", "mF_9.B5f-4.1JqM")
            .send();

        assertThat(response.code()).isEqualTo(401);

        server.verify(WireMock
            .requestMadeFor(
                    request -> MatchResult.aggregate(MatchResult.of(request.getMethod().getName().equals("POST")),
                            MatchResult.of(request.getUrl().equals("/metrics"))))
            .withHeader("Authorization", equalTo("Bearer mF_9.B5f-4.1JqM")));
    }

}
