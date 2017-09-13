/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.composite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeCounterTest {
    @Test
    @Issue("#119")
    void increment() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        registry.add(simple);

        registry.counter("counter").increment(2.0);

        assertThat(simple.find("counter").value(Statistic.Count, 2.0).counter()).isPresent();
    }
}
