/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.simple;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    public void toStringOutputTest() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        final Counter requestSizeCounter = Counter.builder("request.size")
                .baseUnit("bytes")
                .register(registry);

        final DistributionSummary responseSizeSummary = DistributionSummary.builder("response.size")
                .baseUnit("bytes")
                .publishPercentileHistogram()
                .publishPercentiles(0.9999, 0.99, 0.95, 0.90, 0.75)
                .sla(250, 500, 1000, 2000, 4000, 6000)
                .register(registry);

        final Timer responseTimer = Timer.builder("response.time")
                .tag("action", "create")
                .publishPercentiles(0.999, 0.99, 0.95, 0.90, 0.75)
                .sla(Duration.ofMillis(20), Duration.ofMillis(50), Duration.ofMillis(100))
                .publishPercentileHistogram()
                .register(registry);

        final AtomicLong testRunTime = new AtomicLong();
        TimeGauge
                .builder("test.run.time", testRunTime, TimeUnit.NANOSECONDS, AtomicLong::doubleValue)
                .register(registry);

        FunctionTimer
                .builder("cache.latency", new Object(), o -> 10, o -> 300, TimeUnit.MILLISECONDS)
                .register(registry);

        FunctionCounter
                .builder("cache.evictions", new Object(), o -> 10)
                .baseUnit("beans")
                .register(registry);

        final LongTaskTimer taskTimer = LongTaskTimer
                .builder("test.loop.timer")
                .register(registry);

        Meter.builder("default.meter", Type.GAUGE,
                Arrays.asList(
                        new Measurement(() -> 7.7, Statistic.VALUE),
                        new Measurement(() -> 8.8, Statistic.VALUE)
                ))
                .baseUnit("beans")
                .register(registry);

        final LongTaskTimer.Sample loopSample = taskTimer.start();

        for (int i = 0; i < 300; i++) {
            responseTimer.record(Duration.ofMillis(i));
            requestSizeCounter.increment(i);
            responseSizeSummary.record(i);
        }

        loopSample.stop();

        testRunTime.set(200);
        assertThat(registry.toString()).isEqualTo("\n"
                + "Counter cache.evictions count=10.000000\n"
                + "Timer cache.latency\n"
                + "        count = 10.000000\n"
                + "    totalTime = 0.300000\n"
                + "         mean = 0.030000\n"
                + "Gauge default.meter\n"
                + "        VALUE = 7.700000\n"
                + "        VALUE = 8.800000\n"
                + "Counter request.size count=44850.000000\n"
                + "DistributionSummary response.size\n"
                + "        count = 300\n"
                + "        total = 44850.000000\n"
                + "         mean = 149.500000\n"
                + "          max = 299.000000\n"
                + "    percentileValues:\n"
                + "        75.0 <= 231.9375\n"
                + "        90.0 <= 271.9375\n"
                + "        95.0 <= 287.9375\n"
                + "        99.0 <= 303.9375\n"
                + "       99.99 <= 303.9375\n"
                + "    histogramCounts:\n"
                + "       250.0 <= 256.0\n"
                + "       500.0 <= 300.0\n"
                + "      1000.0 <= 300.0\n"
                + "      2000.0 <= 300.0\n"
                + "      4000.0 <= 300.0\n"
                + "      6000.0 <= 300.0\n"
                + "Gauge response.size.histogram {le=250} value=256.000000\n"
                + "Gauge response.size.histogram {le=500} value=300.000000\n"
                + "Gauge response.size.histogram {le=1000} value=300.000000\n"
                + "Gauge response.size.histogram {le=2000} value=300.000000\n"
                + "Gauge response.size.histogram {le=4000} value=300.000000\n"
                + "Gauge response.size.histogram {le=6000} value=300.000000\n"
                + "Gauge response.size.percentile {phi=0.9} value=271.937500\n"
                + "Gauge response.size.percentile {phi=0.75} value=231.937500\n"
                + "Gauge response.size.percentile {phi=0.95} value=287.937500\n"
                + "Gauge response.size.percentile {phi=0.99} value=303.937500\n"
                + "Gauge response.size.percentile {phi=0.9999} value=303.937500\n"
                + "Timer response.time {action=create}\n"
                + "        count = 300\n"
                + "        total = 44.850000\n"
                + "         mean = 0.149500\n"
                + "          max = 0.299000\n"
                + "    percentileValues:\n"
                + "        75.0 <= 0.226459648\n"
                + "        90.0 <= 0.285179904\n"
                + "        95.0 <= 0.285179904\n"
                + "        99.0 <= 0.30195712\n"
                + "        99.9 <= 0.30195712\n"
                + "    histogramCounts:\n"
                + "        0.02 <= 21.0\n"
                + "        0.05 <= 51.0\n"
                + "         0.1 <= 101.0\n"
                + "Gauge response.time.histogram {action=create; le=0.1} value=101.000000\n"
                + "Gauge response.time.histogram {action=create; le=0.02} value=21.000000\n"
                + "Gauge response.time.histogram {action=create; le=0.05} value=51.000000\n"
                + "Gauge response.time.percentile {action=create; phi=0.9} value=0.285180\n"
                + "Gauge response.time.percentile {action=create; phi=0.75} value=0.226460\n"
                + "Gauge response.time.percentile {action=create; phi=0.95} value=0.285180\n"
                + "Gauge response.time.percentile {action=create; phi=0.99} value=0.301957\n"
                + "Gauge response.time.percentile {action=create; phi=0.999} value=0.301957\n"
                + "Timer test.loop.timer\n"
                + "  activeTasks = 0\n"
                + "     duration = 0.000000\n"
                + "Gauge test.run.time value=0.000000");
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
