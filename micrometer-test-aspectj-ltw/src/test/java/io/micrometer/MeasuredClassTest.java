/*
 * Copyright 2024
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
package io.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MeasuredClassTest {

    static MeterRegistry meterRegistry;

    @BeforeAll
    static void setUpAll() {
        meterRegistry = new SimpleMeterRegistry();
        Metrics.addRegistry(meterRegistry);
    }

    MeasuredClass measuredClass;

    @BeforeEach
    void setUp() {
        measuredClass = new MeasuredClass();
    }

    @Test
    void it_measures_timed_method() {
        measuredClass.timedMethod();

        var timer = meterRegistry.get("method.timed")
            .tag("class", MeasuredClass.class.getName())
            .tag("method", "timedMethod")
            .timer();

        assertThat(timer.count()).isOne();

        measuredClass.timedMethod();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    void it_measures_counted_method() {
        measuredClass.countedMethod();

        var counter = meterRegistry.get("method.counted")
            .tag("class", MeasuredClass.class.getName())
            .tag("method", "countedMethod")
            .counter();

        assertThat(counter.count()).isOne();

        measuredClass.countedMethod();
        assertThat(counter.count()).isEqualTo(2);
    }

    @Test
    void it_measures_class_level_timed_method() {
        measuredClass.classLevelTimedMethod();

        var timer = meterRegistry.get("method.timed")
            .tag("class", MeasuredClass.class.getName())
            .tag("method", "classLevelTimedMethod")
            .timer();

        assertThat(timer.count()).isOne();

        measuredClass.classLevelTimedMethod();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    void it_measures_class_level_counted_method() {
        measuredClass.classLevelCountedMethod();

        var counter = meterRegistry.get("method.counted")
            .tag("class", MeasuredClass.class.getName())
            .tag("method", "classLevelCountedMethod")
            .counter();

        assertThat(counter.count()).isOne();

        measuredClass.classLevelCountedMethod();
        assertThat(counter.count()).isEqualTo(2);
    }

}
