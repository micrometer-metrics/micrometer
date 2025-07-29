/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.testsupport.system.CapturedOutput;
import io.micrometer.core.testsupport.system.OutputCaptureExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractTimer}.
 *
 * @author Johnny Lim
 */
@ExtendWith(OutputCaptureExtension.class)
class AbstractTimerTests {

    @Test
    void recordWhenValueIsNegativeShouldLogWarnAndIgnore(CapturedOutput output) {
        AbstractTimer timer = new MyTimer();
        timer.record(-1, TimeUnit.SECONDS);
        assertThat(output).contains("'amount' should not be negative but was: -1")
            .contains("recordWhenValueIsNegativeShouldLogWarnAndIgnore");
        timer.record(-2, TimeUnit.SECONDS);
        assertThat(output).doesNotContain("'amount' should not be negative but was: -2");
    }

    @Test
    void recordWhenValueIsNegativeShouldLogWarnAndDebug(CapturedOutput output)
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        setLogLevelToDebug();

        AbstractTimer timer = new MyTimer();
        timer.record(-1, TimeUnit.SECONDS);
        assertThat(output).contains("'amount' should not be negative but was: -1")
            .contains("recordWhenValueIsNegativeShouldLogWarnAndDebug");
        timer.record(-2, TimeUnit.SECONDS);
        assertThat(output).contains("'amount' should not be negative but was: -2");
    }

    private void setLogLevelToDebug() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        InternalLogger internalLogger = InternalLoggerFactory.getInstance(AbstractTimer.class);
        Class<?> clazz = Class.forName("io.micrometer.common.util.internal.logging.LocationAwareSlf4JLogger");
        Field loggerField = clazz.getDeclaredField("logger");
        loggerField.setAccessible(true);
        Logger logbackLogger = (Logger) loggerField.get(internalLogger);
        logbackLogger.setLevel(Level.DEBUG);
    }

    static class MyTimer extends AbstractTimer {

        MyTimer() {
            super(new Meter.Id("name", Tags.empty(), null, null, Meter.Type.TIMER), Clock.SYSTEM,
                    DistributionStatisticConfig.DEFAULT, new NoPauseDetector(), TimeUnit.SECONDS, false);
        }

        @Override
        protected void recordNonNegative(long amount, TimeUnit unit) {
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public double totalTime(TimeUnit unit) {
            return 0;
        }

        @Override
        public double max(TimeUnit unit) {
            return 0;
        }

    }

}
