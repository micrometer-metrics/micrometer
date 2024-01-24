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
package io.micrometer.wavefront;

import com.wavefront.sdk.common.clients.service.token.TokenService.Type;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WavefrontConfigTest {

    @Issue("#3903")
    @Test
    void defaultUriImplementationWorksWithProxyUri() throws IOException {
        Properties wavefrontProps = new Properties();
        wavefrontProps.load(this.getClass().getResourceAsStream("/valid.properties"));
        WavefrontConfig config = wavefrontProps::getProperty;
        assertThatCode(config::uri).doesNotThrowAnyException();
    }

    @Test
    void defaultUriImplementationThrowsForInvalidUri() throws IOException {
        Properties wavefrontProps = new Properties();
        wavefrontProps.load(this.getClass().getResourceAsStream("/invalid.properties"));
        WavefrontConfig config = wavefrontProps::getProperty;
        assertThatCode(config::uri).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("it must be a valid URI");
    }

    @Test
    void noExceptionAndNoTokenTypeIfTokenMissingButProxyUsed() {
        Map<String, String> wavefrontProps = Map.of("wavefront.uri", "proxy://example.org:2878");
        WavefrontConfig config = wavefrontProps::get;
        assertThatCode(config::apiToken).doesNotThrowAnyException();
        assertThat(config.apiTokenType()).isSameAs(Type.NO_TOKEN);
    }

    @Test
    void noExceptionAndWavefrontTokenTypeIfTokenPresentAndDirectAccessUsed() {
        Map<String, String> wavefrontProps = Map.of("wavefront.uri", "https://example.org:2878", "wavefront.apiToken",
                "s3cr3t");
        WavefrontConfig config = wavefrontProps::get;
        assertThatCode(config::apiToken).doesNotThrowAnyException();
        assertThat(config.apiTokenType()).isSameAs(Type.WAVEFRONT_API_TOKEN);
    }

    @Test
    void apiTokenFailsIfNoTokenPresentAndDirectAccessUsed() {
        Map<String, String> wavefrontProps = Map.of("wavefront.uri", "https://example.org:2878");
        WavefrontConfig config = wavefrontProps::get;
        assertThatCode(config::apiToken).isExactlyInstanceOf(ValidationException.class)
            .hasNoCause()
            .hasMessage(
                    "wavefront.apiToken was 'null' but it must be set whenever publishing directly to the Wavefront API");
    }

    @Test
    void apiTokenTypeFailsIfNoTokenTypeAndDirectAccessUsed() {
        Map<String, String> wavefrontProps = Map.of("wavefront.uri", "https://example.org:2878",
                "wavefront.apiTokenType", Type.NO_TOKEN.name());
        WavefrontConfig config = wavefrontProps::get;
        assertThatCode(config::apiTokenType).isExactlyInstanceOf(ValidationException.class)
            .hasNoCause()
            .hasMessage(
                    "wavefront.apiTokenType was 'No-Op/Proxy' but it must be set to something else whenever publishing directly to the Wavefront API");
    }

    @Test
    void apiTokenTypeShouldBeUsedIfDirectAccessUsed() {
        Map<String, String> wavefrontProps = Map.of("wavefront.uri", "https://example.org:2878",
                "wavefront.apiTokenType", Type.CSP_API_TOKEN.name());
        WavefrontConfig config = wavefrontProps::get;
        assertThat(config.apiTokenType()).isSameAs(Type.CSP_API_TOKEN);
    }

}
