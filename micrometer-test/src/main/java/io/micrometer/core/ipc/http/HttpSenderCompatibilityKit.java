/**
 * Copyright 2018 Pivotal Software, Inc.
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A compatibility kit that can be used to verify user-provided {@link HttpSender} implementations.
 *
 * @author Jon Schneider
 */
@ExtendWith({
        HttpClientResolver.class,
        WiremockResolver.class
})
public abstract class HttpSenderCompatibilityKit {
    public abstract HttpSender httpClient();

    @Test
    @DisplayName("compatibility test provides a non-null http client instance")
    void httpClientIsNotNull(HttpSender httpClient) {
        assertThat(httpClient).isNotNull();
    }

    @Test
    void successfulPostWithBody(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()).willReturn(aResponse()
                .withBody("a body")));

        assertThat(httpClient.post(server.baseUrl() + "/api")
                .withPlainText("this is a line")
                .send().body()).isEqualTo("a body");

        server.verify(postRequestedFor(urlEqualTo("/api")).withRequestBody(equalTo("this is a line")));
    }

    @Test
    void successfulPostNoBody(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()));

        assertThat(httpClient.post(server.baseUrl() + "/api")
                .withPlainText("this is a line")
                .send().body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);

        server.verify(postRequestedFor(urlEqualTo("/api")).withRequestBody(equalTo("this is a line")));
    }

    @Test
    void failedPostWithBody(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(500)
                .withBody("a body")));

        assertThat(httpClient.post(server.baseUrl() + "/api").send().body()).isEqualTo("a body");
    }

    @Test
    void failedPostWithNoBody(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(500)));

        assertThat(httpClient.post(server.baseUrl() + "/api").send().body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);
    }

    @Test
    void successfulHead(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()));

        assertThat(httpClient.head(server.baseUrl() + "/api")
                .send().body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);
    }

    @Test
    void failedHeadWithNoBody(HttpSender httpClient, @WiremockResolver.Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500)));

        assertThat(httpClient.head(server.baseUrl() + "/api")
                .send().body()).isEqualTo(HttpSender.Response.NO_RESPONSE_BODY);
    }
}