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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StepFunctionCounterTest {

    private MockClock clock = new MockClock();

    private StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private MeterRegistry registry = new StepMeterRegistry(config, clock) {
        @Override
        protected void publish() {
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }
    };

    @Test
    void count() {
        AtomicInteger n = new AtomicInteger(1);
        FunctionCounter counter = registry.more().counter("my.counter", Tags.empty(), n);

        assertThat(counter).isInstanceOf(StepFunctionCounter.class);
        assertThat(counter.count()).isEqualTo(0);
        clock.add(config.step());
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void closingRolloverPartialStep() {
        AtomicInteger n = new AtomicInteger(3);
        @SuppressWarnings({ "rawtypes", "unchecked" })
        StepFunctionCounter<AtomicInteger> counter = (StepFunctionCounter) registry.more()
            .counter("my.counter", Tags.empty(), n);

        assertThat(counter.count()).isZero();

        counter._closingRollover();

        assertThat(counter.count()).isEqualTo(3);

        clock.add(config.step());

        assertThat(counter.count()).isEqualTo(3);
    }

}
