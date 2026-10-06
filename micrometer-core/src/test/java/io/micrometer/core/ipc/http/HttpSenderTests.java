/*
 * Copyright 2026 VMware, Inc.
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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HttpSenderTests {

    @ParameterizedTest
    @ValueSource(strings = { " secret", "secret ", " secret ", " ", "secret", "open sesame" })
    void basicAuthenticationPreservesPassword(String password) throws Throwable {
        HttpSender sender = request -> {
            String authorization = request.getRequestHeaders().get("Authorization");
            assertThat(authorization).isNotNull().startsWith("Basic ");
            String credentials = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                    StandardCharsets.UTF_8);
            assertThat(credentials).isEqualTo("user:" + password);
            return new HttpSender.Response(200, null);
        };

        sender.get("https://example.com").withBasicAuthentication("user", password).send();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void basicAuthenticationAllowsEmptyPassword(@Nullable String password) throws Throwable {
        HttpSender sender = request -> {
            assertThat(request.getRequestHeaders()).containsEntry("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString("user:".getBytes(StandardCharsets.UTF_8)));
            return new HttpSender.Response(200, null);
        };

        sender.get("https://example.com").withBasicAuthentication("user", password).send();
    }

}
