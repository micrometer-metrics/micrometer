/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.ipc.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpStatusClass}
 * 
 * @author Oleksii Bondar
 */
class HttpStatusClassTest {

    @ParameterizedTest
    @CsvSource({"100, INFORMATIONAL", "199, INFORMATIONAL", "200, SUCCESS", "204, SUCCESS", "301, REDIRECTION", "404, CLIENT_ERROR", "500, SERVER_ERROR", "777, UNKNOWN"})
    void resolveStatus(int code, HttpStatusClass expected) {
        assertThat(HttpStatusClass.valueOf(code)).isEqualTo(expected);
    }

    @Test
    void containsStatusCode() {
        assertThat(HttpStatusClass.CLIENT_ERROR.contains(401)).isTrue();
    }

    @Test
    void doesNotContainStatusCode() {
        assertThat(HttpStatusClass.CLIENT_ERROR.contains(200)).isFalse();
    }
}
