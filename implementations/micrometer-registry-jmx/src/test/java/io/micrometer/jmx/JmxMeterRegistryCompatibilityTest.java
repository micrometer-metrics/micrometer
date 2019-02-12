/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.jmx;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.LoggerFactory;

import java.time.Duration;

class JmxMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {
    @BeforeAll
    static void before() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    @Override
    public MeterRegistry registry() {
        return new JmxMeterRegistry(JmxConfig.DEFAULT, new MockClock(), HierarchicalNameMapper.DEFAULT);
    }

    @Override
    public Duration step() {
        return JmxConfig.DEFAULT.step();
    }
}
