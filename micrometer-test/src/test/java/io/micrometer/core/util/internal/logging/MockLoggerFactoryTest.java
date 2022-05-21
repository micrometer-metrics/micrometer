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
package io.micrometer.core.util.internal.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonatan Ivanov
 */
@Deprecated
class MockLoggerFactoryTest {

    private static final MockLoggerFactory FACTORY = new MockLoggerFactory();

    @Test
    void shouldGiveTheRightLoggerByName() {
        InternalLogger logger = FACTORY.getLogger("testLogger");

        assertThat(logger).isInstanceOf(MockLogger.class);
        assertThat(logger.name()).isEqualTo("testLogger");
    }

    @Test
    void shouldGiveTheRightLoggerByClassName() {
        InternalLogger logger = FACTORY.getLogger(MockLoggerFactoryTest.class);

        assertThat(logger).isInstanceOf(MockLogger.class);
        assertThat(logger.name()).isEqualTo(MockLoggerFactoryTest.class.getName());
    }

    @Test
    void shouldGiveTheSameLoggerForTheSameName() {
        InternalLogger logger01 = FACTORY.getLogger("testLogger");
        InternalLogger logger02 = FACTORY.getLogger("testLogger");

        assertThat(logger01).isSameAs(logger02);
    }

    @Test
    void shouldGiveTheSameLoggerForTheSameClassName() {
        InternalLogger logger01 = FACTORY.getLogger(MockLoggerFactoryTest.class);
        InternalLogger logger02 = FACTORY.getLogger(MockLoggerFactoryTest.class);

        assertThat(logger01).isSameAs(logger02);
    }

    @Test
    void shouldGiveDifferentLoggersForDifferentNames() {
        InternalLogger logger01 = FACTORY.getLogger("testLogger");
        InternalLogger logger02 = FACTORY.getLogger("testLogger-2");

        assertThat(logger01).isNotSameAs(logger02);
    }

    @Test
    void shouldGiveDifferentLoggersForDifferentClassNames() {
        InternalLogger logger01 = FACTORY.getLogger(MockLoggerFactoryTest.class);
        InternalLogger logger02 = FACTORY.getLogger(MockLoggerFactory.class);

        assertThat(logger01).isNotSameAs(logger02);
    }

    @Test
    void shouldBeAbleToInjectAMockedLogger() {
        TestComponentWithLogger component = FACTORY.injectLogger(TestComponentWithLogger::new);
        assertThat(component.getLogger()).isSameAs(FACTORY.getLogger(TestComponentWithLogger.class));
    }

    private static class TestComponentWithLogger {

        private final InternalLogger logger = InternalLoggerFactory.getInstance(TestComponentWithLogger.class);

        private InternalLogger getLogger() {
            return this.logger;
        }

    }

}
