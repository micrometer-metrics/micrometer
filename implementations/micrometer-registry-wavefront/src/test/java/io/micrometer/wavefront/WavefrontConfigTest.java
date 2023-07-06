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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

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

}
