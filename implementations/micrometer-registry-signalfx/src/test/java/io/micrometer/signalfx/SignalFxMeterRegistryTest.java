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
package io.micrometer.signalfx;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SignalFxMeterRegistry}.
 *
 * @author Johnny Lim
 */
class SignalFxMeterRegistryTest {

    private final SignalFxConfig config = new SignalFxConfig() {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }

    };

    private final SignalFxMeterRegistry registry = new SignalFxMeterRegistry(this.config, new MockClock());

    @Test
    void addLongTaskTimer() {
        LongTaskTimer longTaskTimer = LongTaskTimer.builder("my.long.task.timer").register(this.registry);
        assertThat(this.registry.addLongTaskTimer(longTaskTimer)).hasSize(2);
    }

}
