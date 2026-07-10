/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CloudWatchNamingConvention}.
 *
 * @author Klaus Hartl
 * @author Johnny Lim
 */
class CloudWatchNamingConventionTest {

    private final NamingConvention namingConvention = new CloudWatchNamingConvention();

    @Test
    void truncateTagKey() {
        assertThat(namingConvention.tagKey(repeat("x", 256))).hasSize(255);
    }

    @Test
    void truncateTagValue() {
        assertThat(namingConvention.tagValue(repeat("x", 1025))).hasSize(1024);
    }

    private String repeat(String s, int repeat) {
        return String.join("", Collections.nCopies(repeat, s));
    }

}
