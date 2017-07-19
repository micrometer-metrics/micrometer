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
package io.micrometer.core.instrument.binder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackMetricsTest {
    @Test
    /* FIXME */
    @Disabled("why is this flaky on CircleCI")
    void logbackLevelMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new LogbackMetrics().bindTo(registry);

        assertThat(registry.findMeter(Counter.class, "logback_events"))
                .containsInstanceOf(Counter.class)
                .hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(0));

        Logger logger = LoggerFactory.getLogger("foo");
        logger.warn("warn");
        logger.error("error");

        assertThat(registry.findMeter(Counter.class, "logback_events", "level", "warn"))
                .containsInstanceOf(Counter.class)
                .hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));
    }
}
