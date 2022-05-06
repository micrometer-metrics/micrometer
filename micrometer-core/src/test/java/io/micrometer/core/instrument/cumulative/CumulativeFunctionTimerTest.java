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
package io.micrometer.core.instrument.cumulative;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CumulativeFunctionTimer}.
 *
 * @author Johnny Lim
 */
class CumulativeFunctionTimerTest {

    @Test
    void totalTimeWhenStateObjectChangedToNullShouldWorkWithChangedTimeUnit() {
        CumulativeFunctionTimer<Object> functionTimer = new CumulativeFunctionTimer<>(null, new Object(), (o) -> 1L,
                (o) -> 1d, TimeUnit.SECONDS, TimeUnit.SECONDS);
        assertThat(functionTimer.totalTime(TimeUnit.SECONDS)).isEqualTo(1d);
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1000d);
        System.gc();
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1000d);
        assertThat(functionTimer.totalTime(TimeUnit.SECONDS)).isEqualTo(1d);
    }

}
