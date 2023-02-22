/*
 * Copyright 2019 VMware, Inc.
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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@WireMockTest
class OkHttpSenderTests {

    HttpSender httpSender = new OkHttpSender();

    @Test
    void customReadTimeoutHonored(WireMockRuntimeInfo wmInfo) {
        this.httpSender = new OkHttpSender(new OkHttpClient.Builder().readTimeout(1, TimeUnit.MILLISECONDS).build());
        stubFor(any(urlEqualTo("/metrics")).willReturn(ok().withFixedDelay(5)));

        assertThatExceptionOfType(SocketTimeoutException.class)
            .isThrownBy(() -> httpSender.post(wmInfo.getHttpBaseUrl() + "/metrics").send());
    }

    @Test
    void appendUtf8CharsetContentType(WireMockRuntimeInfo wmInfo) throws Throwable {
        stubFor(any(urlEqualTo("/metrics")));

        this.httpSender.post(wmInfo.getHttpBaseUrl() + "/metrics").withContent("application/xml", "<xml></xml>").send();

        verify(postRequestedFor(urlEqualTo("/metrics")).withHeader("Content-Type",
                equalTo("application/xml; charset=utf-8")));
    }

}
