/**
 * Copyright 2019 VMware, Inc.
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
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.netty.http.client.HttpClient;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@ExtendWith(WiremockResolver.class)
class ReactorNettySenderTests {
    HttpSender httpSender = new ReactorNettySender();

    @Test
    void customReadTimeoutHonored(@WiremockResolver.Wiremock WireMockServer server) {
        this.httpSender = new ReactorNettySender(HttpClient.create()
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(1, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(1, TimeUnit.MILLISECONDS))));
        server.stubFor(any(urlEqualTo("/metrics")).willReturn(ok().withFixedDelay(5)));

        assertThatExceptionOfType(ReadTimeoutException.class)
                .isThrownBy(() -> httpSender.post(server.baseUrl() + "/metrics").send());
    }
}
