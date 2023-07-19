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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StepCounterTest {

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
        Counter counter = registry.counter("my.counter");

        assertThat(counter).isInstanceOf(StepCounter.class);
        assertThat(counter.count()).isEqualTo(0);
        counter.increment();
        clock.add(config.step());
        assertThat(counter.count()).isEqualTo(1);
        counter.increment();
        counter.increment(5);
        clock.add(config.step());
        assertThat(counter.count()).isEqualTo(6);
        clock.add(config.step());
        assertThat(counter.count()).isEqualTo(0);
    }

    @Test
    void closingRolloverPartialStep() {
        StepCounter counter = (StepCounter) registry.counter("my.counter");
        counter.increment(2.5);

        assertThat(counter.count()).isZero();
        counter._closingRollover();
        assertThat(counter.count()).isEqualTo(2.5);

        clock.add(config.step());
        assertThat(counter.count()).isEqualTo(2.5);
    }

}
