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
package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LogbackMetricsTest {
    @Test
    void logbackLevelMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new LogbackMetrics());

        assertLogCounter(registry, 0);

        Logger logger = LoggerFactory.getLogger("foo");
        logger.warn("warn");
        logger.error("error");

        assertLogCounter(registry, 2);
    }

    private void assertLogCounter(MeterRegistry registry, int n) {
        assertThat(registry.find("logback_events").stream()
                .map(c -> ((Counter) c).count())
                .reduce(Double::sum)
                .get()).isEqualTo(n, offset(1.0e-12));
    }
}
