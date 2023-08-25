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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Meter;
import io.micrometer.dynatrace.v1.DynatraceNamingConventionV1;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DynatraceNamingConvention}.
 *
 * @author Georg Pirklbauer
 */
class DynatraceNamingConventionTest {

    private final DynatraceNamingConvention dynatraceConvention = new DynatraceNamingConvention();

    @Test
    void testDelegate() {
        DynatraceNamingConventionV1 v1Convention = new DynatraceNamingConventionV1();
        assertThat(dynatraceConvention.name("mymetric", Meter.Type.COUNTER, null))
            .isEqualTo(v1Convention.name("mymetric", Meter.Type.COUNTER, null));
        assertThat(dynatraceConvention.name("my.name1", Meter.Type.COUNTER, null))
            .isEqualTo(v1Convention.name("my.name1", Meter.Type.COUNTER, null));
        assertThat(dynatraceConvention.name("my_name1", Meter.Type.COUNTER, null))
            .isEqualTo(v1Convention.name("my_name1", Meter.Type.COUNTER, null));
        assertThat(dynatraceConvention.name("my-name1", Meter.Type.COUNTER, null))
            .isEqualTo(v1Convention.name("my-name1", Meter.Type.COUNTER, null));
        assertThat(dynatraceConvention.name("system.load.average.1m", Meter.Type.COUNTER, null))
            .isEqualTo(v1Convention.name("system.load.average.1m", Meter.Type.COUNTER, null));
        assertThat(dynatraceConvention.tagKey("{tagTag0}.-")).isEqualTo(v1Convention.tagKey("_tagTag0_.-"));
    }

}
