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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TimeGaugeTest {

    @Test
    void hasBaseTimeUnit() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AtomicLong n = new AtomicLong();
        TimeGauge g = registry.more()
            .timeGauge("my.time.gauge", Tags.empty(), n, TimeUnit.SECONDS, AtomicLong::doubleValue);

        assertThat(g.getId().getBaseUnit()).isEqualTo("seconds");
    }

}
