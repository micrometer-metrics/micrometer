/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.config.NamingConvention;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class CloudWatchNamingConventionTest {
    private final NamingConvention namingConvention = new CloudWatchNamingConvention();

    @Test
    void truncateTagKey() {
        assertThat(namingConvention
                .tagKey(StringUtils.repeat("x", 256)).length()).isEqualTo(255);
    }

    @Test
    void truncateTagValue() {
        assertThat(namingConvention
                .tagValue(StringUtils.repeat("x", 256)).length()).isEqualTo(255);
    }

}
