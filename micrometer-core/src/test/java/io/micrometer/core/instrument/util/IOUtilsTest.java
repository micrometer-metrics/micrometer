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
package io.micrometer.core.instrument.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IOUtils}.
 *
 * @author Johnny Lim
 */
class IOUtilsTest {

    @Test
    void testToString() {
        String expected = "This is a sample.";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(expected.getBytes());

        assertThat(IOUtils.toString(inputStream)).isEqualTo(expected);
    }

    @Test
    void testToStringWithCharset() {
        String expected = "This is a sample.";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));

        assertThat(IOUtils.toString(inputStream, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

}
