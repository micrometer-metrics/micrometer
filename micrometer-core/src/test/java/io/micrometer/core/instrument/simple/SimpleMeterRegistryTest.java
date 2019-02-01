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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class SimpleMeterRegistryTest {
    private MockClock clock = new MockClock();
    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

    @Issue("#370")
    @Test
    void slasOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").sla(1, 2).register(registry);
        summary.record(1);

        Timer timer = Timer.builder("my.timer").sla(Duration.ofMillis(1)).register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "0.001").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(timerHist.value()).isEqualTo(1);

        clock.add(SimpleConfig.DEFAULT.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(timerHist.value()).isEqualTo(0);
    }

    @Test
    public void newFunctionTimerWhenCountingModeIsCumulativeShouldReturnCumulativeFunctionTimer() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.CUMULATIVE);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.TIMER);
        FunctionTimer functionTimer = registry.newFunctionTimer(id, null, (o) -> 0L, (o) -> 0d, TimeUnit.SECONDS);
        assertThat(functionTimer).isInstanceOf(CumulativeFunctionTimer.class);
    }

    @Test
    public void newFunctionCounterWhenCountingModeIsCumulativeShouldReturnCumulativeFunctionCounter() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.CUMULATIVE);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.COUNTER);
        FunctionCounter functionCounter = registry.newFunctionCounter(id, null, (o) -> 0d);
        assertThat(functionCounter).isInstanceOf(CumulativeFunctionCounter.class);
    }

    @Test
    public void newFunctionTimerWhenCountingModeIsStepShouldReturnStepFunctionTimer() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.STEP);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.TIMER);
        FunctionTimer functionTimer = registry.newFunctionTimer(id, null, (o) -> 0L, (o) -> 0d, TimeUnit.SECONDS);
        assertThat(functionTimer).isInstanceOf(StepFunctionTimer.class);
    }

    @Test
    public void newFunctionCounterWhenCountingModeIsStepShouldReturnStepFunctionCounter() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.STEP);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.COUNTER);
        FunctionCounter functionCounter = registry.newFunctionCounter(id, null, (o) -> 0d);
        assertThat(functionCounter).isInstanceOf(StepFunctionCounter.class);
    }

    private SimpleMeterRegistry createRegistry(CountingMode mode) {
        return new SimpleMeterRegistry(new SimpleConfig() {

                @Override
                public String get(String key) {
                    return null;
                }

                @Override
                public CountingMode mode() {
                    return mode;
                }

            }, clock);
    }

}