/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.dynatrace.v1;

import io.micrometer.core.instrument.Meter;
import io.micrometer.dynatrace.DynatraceNamingConvention;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DynatraceNamingConvention}.
 *
 * @author Oriol Barcelona Palau
 * @author Jon Schneider
 * @author Johnny Lim
 */
class DynatraceNamingConventionV1Test {

    private final DynatraceNamingConventionV1 convention = new DynatraceNamingConventionV1();

    @Test
    void nameStartsWithCustomAndColon() {
        assertThat(convention.name("mymetric", Meter.Type.COUNTER, null)).isEqualTo("custom:mymetric");
    }

    @Test
    void nameShouldAllowAlphanumericUnderscoreAndDash() {
        assertThat(convention.name("my.name1", Meter.Type.COUNTER, null)).isEqualTo("custom:my.name1");
        assertThat(convention.name("my_name1", Meter.Type.COUNTER, null)).isEqualTo("custom:my_name1");
        assertThat(convention.name("my-name1", Meter.Type.COUNTER, null)).isEqualTo("custom:my-name1");
    }

    @Test
    void nameShouldSanitize() {
        assertThat(convention.name("my,name1", Meter.Type.COUNTER, null)).isEqualTo("custom:my_name1");
    }

    @Test
    void nameWithSystemLoadAverageOneMintueShouldSanitize() {
        assertThat(convention.name("system.load.average.1m", Meter.Type.COUNTER, null))
            .isEqualTo("custom:system.load.average.oneminute");
    }

    @Test
    void tagKeysAreSanitized() {
        assertThat(convention.tagKey("{tagTag0}.-")).isEqualTo("_tagTag0_.-");
    }

    @Test
    void tagValuesAreTruncated() {
        String expected = String.join("", Collections.nCopies(128, "a"));
        assertThat(convention.tagValue(String.join("", Collections.nCopies(130, "a")))).isEqualTo(expected);
    }

}
