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
package io.micrometer.statsd;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.reactivestreams.Processor;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Operators;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StatsdMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class StatsdMeterRegistryTest {

    private MockClock clock = new MockClock();

    private StatsdMeterRegistry registry;

    @AfterEach
    void cleanUp() {
        registry.close();
    }

    private static StatsdConfig configWithFlavor(StatsdFlavor flavor) {
        return new StatsdConfig() {
            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public StatsdFlavor flavor() {
                return flavor;
            }
        };
    }

    @ParameterizedTest
    @EnumSource
    void counterLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case ETSY:
                line = "myCounter.myTag.val.statistic.count:2|c";
                break;
            case DATADOG:
                line = "my.counter:2|c|#statistic:count,my.tag:val";
                break;
            case TELEGRAF:
                line = "my_counter,statistic=count,my_tag=val:2|c";
                break;
            case SYSDIG:
                line = "my.counter#statistic=count,my.tag=val:2|c";
                break;
            default:
                fail("Unexpected flavor");
        }

        final Processor<String, String> lines = lineProcessor();
        registry = StatsdMeterRegistry.builder(configWithFlavor(flavor))
            .clock(clock)
            .lineSink(toLineSink(lines))
            .build();

        StepVerifier.create(lines)
            .then(() -> registry.counter("my.counter", "my.tag", "val").increment(2.1))
            .expectNext(line)
            .verifyComplete();
    }

    @ParameterizedTest
    @EnumSource
    void gaugeLineProtocol(StatsdFlavor flavor) {
        final AtomicInteger n = new AtomicInteger(2);
        final StatsdConfig config = configWithFlavor(flavor);

        String line = null;
        switch (flavor) {
            case ETSY:
                line = "myGauge.myTag.val.statistic.value:2|g";
                break;
            case DATADOG:
                line = "my.gauge:2|g|#statistic:value,my.tag:val";
                break;
            case TELEGRAF:
                line = "my_gauge,statistic=value,my_tag=val:2|g";
                break;
            case SYSDIG:
                line = "my.gauge#statistic=value,my.tag=val:2|g";
                break;
            default:
                fail("Unexpected flavor");
        }

        StepVerifier.withVirtualTime(() -> {
            final Processor<String, String> lines = lineProcessor();
            registry = StatsdMeterRegistry.builder(config).clock(clock).lineSink(toLineSink(lines)).build();

            registry.gauge("my.gauge", Tags.of("my.tag", "val"), n);
            return lines;
        }).then(() -> clock.add(config.step())).thenAwait(config.step()).expectNext(line).verifyComplete();
    }

    @ParameterizedTest
    @EnumSource
    void timerLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case ETSY:
                line = "myTimer.myTag.val:1|ms";
                break;
            case DATADOG:
                line = "my.timer:1|ms|#my.tag:val";
                break;
            case TELEGRAF:
                line = "my_timer,my_tag=val:1|ms";
                break;
            case SYSDIG:
                line = "my.timer#my.tag=val:1|ms";
                break;
            default:
                fail("Unexpected flavor");
        }

        final Processor<String, String> lines = lineProcessor();
        registry = StatsdMeterRegistry.builder(configWithFlavor(flavor))
            .clock(clock)
            .lineSink(toLineSink(lines))
            .build();

        StepVerifier.create(lines)
            .then(() -> registry.timer("my.timer", "my.tag", "val").record(1, TimeUnit.MILLISECONDS))
            .expectNext(line)
            .verifyComplete();
    }

    @ParameterizedTest
    @EnumSource
    void summaryLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case ETSY:
                line = "mySummary.myTag.val:1|h";
                break;
            case DATADOG:
                line = "my.summary:1|h|#my.tag:val";
                break;
            case TELEGRAF:
                line = "my_summary,my_tag=val:1|h";
                break;
            case SYSDIG:
                line = "my.summary#my.tag=val:1|h";
                break;
            default:
                fail("Unexpected flavor");
        }

        final Processor<String, String> lines = lineProcessor();
        registry = StatsdMeterRegistry.builder(configWithFlavor(flavor))
            .clock(clock)
            .lineSink(toLineSink(lines))
            .build();

        StepVerifier.create(lines)
            .then(() -> registry.summary("my.summary", "my.tag", "val").record(1))
            .expectNext(line)
            .verifyComplete();
    }

    @ParameterizedTest
    @EnumSource
    void longTaskTimerLineProtocol(StatsdFlavor flavor) {
        final StatsdConfig config = configWithFlavor(flavor);
        long stepMillis = config.step().toMillis();

        String[] expectLines = null;
        switch (flavor) {
            case ETSY:
                expectLines = new String[] { "myLongTask.myTag.val.statistic.active:1|g",
                        "myLongTask.myTag.val.statistic.duration:" + stepMillis + "|g", };
                break;
            case DATADOG:
                expectLines = new String[] { "my.long.task:1|g|#statistic:active,my.tag:val",
                        "my.long.task:" + stepMillis + "|g|#statistic:duration,my.tag:val", };
                break;
            case TELEGRAF:
                expectLines = new String[] { "my_long_task,statistic=active,my_tag=val:1|g",
                        "my_long_task,statistic=duration,my_tag=val:" + stepMillis + "|g", };
                break;
            case SYSDIG:
                expectLines = new String[] { "my.long.task#statistic=active,my.tag=val:1|g",
                        "my.long.task#statistic=duration,my.tag=val:" + stepMillis + "|g", };
                break;
            default:
                fail("Unexpected flavor");
        }

        AtomicReference<LongTaskTimer> ltt = new AtomicReference<>();
        AtomicReference<LongTaskTimer.Sample> sample = new AtomicReference<>();

        StepVerifier.withVirtualTime(() -> {
            final Processor<String, String> lines = lineProcessor();
            registry = StatsdMeterRegistry.builder(config).clock(clock).lineSink(toLineSink(lines, 2)).build();

            ltt.set(registry.more().longTaskTimer("my.long.task", "my.tag", "val"));
            return lines;
        })
            .then(() -> sample.set(ltt.get().start()))
            .then(() -> clock.add(config.step()))
            .thenAwait(config.step())
            .expectNext(expectLines[0])
            .expectNext(expectLines[1])
            .verifyComplete();
    }

    @Test
    void customNamingConvention() {
        final Processor<String, String> lines = lineProcessor();
        registry = StatsdMeterRegistry.builder(configWithFlavor(StatsdFlavor.ETSY))
            .nameMapper((id, convention) -> id.getName().toUpperCase(Locale.ROOT))
            .clock(clock)
            .lineSink(toLineSink(lines))
            .build();

        StepVerifier.create(lines)
            .then(() -> registry.counter("my.counter", "my.tag", "val").increment(2.1))
            .expectNext("MY.COUNTER:2|c")
            .verifyComplete();
    }

    @Issue("#411")
    @Test
    void counterIncrementDoesNotCauseStackOverflow() {
        registry = new StatsdMeterRegistry(configWithFlavor(StatsdFlavor.ETSY), clock);
        new LogbackMetrics().bindTo(registry);

        // Cause the processor to get into a state that would make it perform logging at
        // DEBUG level.
        ((Logger) LoggerFactory.getLogger(Operators.class)).setLevel(Level.DEBUG);
        registry.processor.onComplete();

        registry.counter("my.counter").increment();
    }

    @ParameterizedTest
    @EnumSource
    @Issue("#370")
    void serviceLevelObjectivesOnlyNoPercentileHistogram(StatsdFlavor flavor) {
        StatsdConfig config = configWithFlavor(flavor);
        registry = new StatsdMeterRegistry(config, clock);
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0, 2)
            .register(registry);
        summary.record(1);

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge summaryHist3 = registry.get("my.summary.histogram").tags("le", "+Inf").gauge();
        Gauge timerHist1 = registry.get("my.timer.histogram").tags("le", "1").gauge();
        Gauge timerHist2 = registry.get("my.timer.histogram").tags("le", "+Inf").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(summaryHist3.value()).isEqualTo(1);
        assertThat(timerHist1.value()).isEqualTo(1);
        assertThat(timerHist2.value()).isEqualTo(1);

        clock.add(config.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(summaryHist3.value()).isEqualTo(0);
        assertThat(timerHist1.value()).isEqualTo(0);
        assertThat(timerHist2.value()).isEqualTo(0);
    }

    @Test
    void timersWithServiceLevelObjectivesHaveInfBucket() {
        registry = new StatsdMeterRegistry(configWithFlavor(StatsdFlavor.ETSY), clock);
        Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);

        // A io.micrometer.core.instrument.search.MeterNotFoundException is thrown if the
        // gauge isn't present
        registry.get("my.timer.histogram").tag("le", "+Inf").gauge();
    }

    @Test
    void distributionSummariesWithServiceLevelObjectivesHaveInfBucket() {
        registry = new StatsdMeterRegistry(configWithFlavor(StatsdFlavor.ETSY), clock);
        DistributionSummary summary = DistributionSummary.builder("my.distribution")
            .serviceLevelObjectives(1.0)
            .register(registry);

        // A io.micrometer.core.instrument.search.MeterNotFoundException is thrown if the
        // gauge isn't present
        registry.get("my.distribution.histogram").tag("le", "+Inf").gauge();
    }

    @Test
    void infBucketEqualsCount() {
        registry = new StatsdMeterRegistry(configWithFlavor(StatsdFlavor.ETSY), clock);
        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "+Inf").gauge();
        Long count = timer.takeSnapshot().count();

        assertThat(timerHist.value()).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void summarySentAsDatadogDistribution_whenPercentileHistogramEnabled() {
        final Processor<String, String> lines = lineProcessor();
        final StatsdConfig config = configWithFlavor(StatsdFlavor.DATADOG);
        registry = StatsdMeterRegistry.builder(config).clock(clock).lineSink(toLineSink(lines, 2)).build();

        StepVerifier.create(lines).then(() -> {
            DistributionSummary.builder("my.summary2").publishPercentileHistogram(false).register(registry).record(20);
            DistributionSummary.builder("my.summary").publishPercentileHistogram(true).register(registry).record(2);
        }).expectNext("my.summary2:20|h").expectNext("my.summary:2|d").verifyComplete();
    }

    @Test
    void timerSentAsDatadogDistribution_whenPercentileHistogramEnabled() {
        final Processor<String, String> lines = lineProcessor();
        final StatsdConfig config = configWithFlavor(StatsdFlavor.DATADOG);
        registry = StatsdMeterRegistry.builder(config).clock(clock).lineSink(toLineSink(lines, 2)).build();

        StepVerifier.create(lines).then(() -> {
            Timer.builder("my.timer").publishPercentileHistogram(true).register(registry).record(2, TimeUnit.SECONDS);
            Timer.builder("my.timer2")
                .publishPercentileHistogram(false)
                .register(registry)
                .record(20, TimeUnit.SECONDS);
        }).expectNext("my.timer:2000|d").expectNext("my.timer2:20000|ms").verifyComplete();
    }

    @Test
    void interactWithStoppedRegistry() {
        registry = new StatsdMeterRegistry(configWithFlavor(StatsdFlavor.ETSY), clock);
        registry.stop();
        registry.counter("my.counter").increment();
    }

    @ParameterizedTest
    @EnumSource
    @Issue("#600")
    void memoryPerformanceOfNamingConventionInHotLoops(StatsdFlavor flavor) {
        AtomicInteger namingConventionUses = new AtomicInteger();

        registry = new StatsdMeterRegistry(configWithFlavor(flavor), clock);

        registry.config().namingConvention(new NamingConvention() {
            @Override
            public String name(String name, Meter.Type type, @Nullable String baseUnit) {
                namingConventionUses.incrementAndGet();
                return NamingConvention.dot.name(name, type, baseUnit);
            }

            @Override
            public String tagKey(String key) {
                namingConventionUses.incrementAndGet();
                return NamingConvention.dot.tagKey(key);
            }

            @Override
            public String tagValue(String value) {
                namingConventionUses.incrementAndGet();
                return NamingConvention.dot.tagValue(value);
            }
        });

        range(0, 100).forEach(n -> registry.counter("my.counter", "k", "v").increment());

        switch (flavor) {
            case DATADOG:
            case TELEGRAF:
                assertThat(namingConventionUses.intValue()).isEqualTo(3);
                break;
            case ETSY:
                // because Etsy formatting involves the naming convention being called on
                // the
                // 'statistic' tag as well.
                assertThat(namingConventionUses.intValue()).isEqualTo(5);
                break;
        }
    }

    @Test
    @Issue("#778")
    void doNotPublishNanOrInfiniteGaugeValues() {
        AtomicInteger lineCount = new AtomicInteger();
        registry = StatsdMeterRegistry.builder(StatsdConfig.DEFAULT).lineSink(l -> lineCount.incrementAndGet()).build();

        AtomicReference<Double> value = new AtomicReference<>(1.0);
        StatsdGauge<?> gauge = (StatsdGauge<?>) Gauge.builder("my.gauge", value, AtomicReference::get)
            .register(registry);

        gauge.poll();
        assertThat(lineCount.get()).isEqualTo(1);

        value.set(Double.NaN);
        gauge.poll();
        assertThat(lineCount.get()).isEqualTo(1);

        value.set(Double.POSITIVE_INFINITY);
        gauge.poll();
        assertThat(lineCount.get()).isEqualTo(1);
    }

    @Test
    @Issue("#1260")
    void lineSinkDoesNotConsumeWhenRegistryDisabled() {
        Consumer<String> shouldNotBeInvokedConsumer = line -> {
            throw new RuntimeException("line sink should not be called");
        };
        registry = StatsdMeterRegistry.builder(new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }).lineSink(shouldNotBeInvokedConsumer).build();

        registry.counter("some.metric").increment();
        assertThat(registry.processor.inners().count()).as("processor has no subscribers registered").isZero();
    }

    @Test
    void stopTrackingMetersThatAreRemoved() {
        Map<String, Integer> lines = new HashMap<>();

        registry = StatsdMeterRegistry.builder(configWithFlavor(StatsdFlavor.ETSY)).clock(clock).lineSink(line -> {
            int firstTag = line.indexOf('.');
            lines.compute(line.substring(0, firstTag == -1 ? line.indexOf(':') : firstTag),
                    (l, count) -> count == null ? 1 : count + 1);
        }).build();

        Meter custom = Meter
            .builder("custom", Meter.Type.COUNTER, singletonList(new Measurement(() -> 1.0, Statistic.COUNT)))
            .register(registry);
        registry.poll();
        registry.remove(custom);
        registry.poll();
        assertThat(lines.get("custom")).isEqualTo(1);

        AtomicInteger tgObj = new AtomicInteger(1);
        registry.more()
            .timeGauge("timegauge", Tags.empty(), tgObj, TimeUnit.MILLISECONDS, AtomicInteger::incrementAndGet);
        registry.poll();
        registry.remove(registry.get("timegauge").timeGauge());
        registry.poll();
        assertThat(lines.get("timegauge")).isEqualTo(1);

        AtomicInteger gaugeObj = new AtomicInteger(1);
        registry.gauge("gauge", gaugeObj, AtomicInteger::incrementAndGet);
        registry.poll();
        registry.remove(registry.get("gauge").gauge());
        registry.poll();
        assertThat(lines.get("gauge")).isEqualTo(1);

        Counter counter = registry.counter("counter");
        counter.increment();
        registry.remove(counter);
        counter.increment();
        assertThat(lines.get("counter")).isEqualTo(1);

        Timer timer = registry.timer("timer");
        timer.record(1, TimeUnit.MILLISECONDS);
        registry.remove(timer);
        timer.record(1, TimeUnit.MILLISECONDS);
        assertThat(lines.get("timer")).isEqualTo(1);

        DistributionSummary summary = registry.summary("summary");
        summary.record(1.0);
        registry.remove(summary);
        summary.record(1.0);
        assertThat(lines.get("summary")).isEqualTo(1);

        LongTaskTimer ltt = registry.more().longTaskTimer("ltt");
        ltt.start();
        registry.poll();
        registry.remove(ltt);
        registry.poll();
        assertThat(lines.get("ltt")).isEqualTo(3); // 3 lines shipped for a LongTaskTimer
                                                   // for each poll

        AtomicInteger ftObj = new AtomicInteger(1);
        registry.more()
            .timer("functiontimer", Tags.empty(), ftObj, AtomicInteger::incrementAndGet, AtomicInteger::get,
                    TimeUnit.MILLISECONDS);
        registry.poll();
        registry.remove(registry.get("functiontimer").functionTimer());
        registry.poll();
        assertThat(lines.get("functiontimer")).isEqualTo(2); // 2 lines shipped for a
                                                             // FunctionTimer for each
                                                             // poll

        AtomicInteger fcObj = new AtomicInteger(1);
        registry.more().counter("functioncounter", Tags.empty(), fcObj, AtomicInteger::incrementAndGet);
        registry.poll();
        registry.remove(registry.get("functioncounter").functionCounter());
        registry.poll();
        assertThat(lines.get("functioncounter")).isEqualTo(1);
    }

    @Test
    void pollFailureNotFatal() {
        registry = StatsdMeterRegistry.builder(StatsdConfig.DEFAULT).build();
        Gauge.builder("fails", () -> {
            throw new RuntimeException();
        }).register(registry);
        Gauge.builder("works", () -> 42).register(registry);

        assertThatCode(() -> registry.poll()).doesNotThrowAnyException();
    }

    @Test
    @Issue("#2064")
    void publishLongTaskTimerMax() throws InterruptedException {
        CountDownLatch maxCount = new CountDownLatch(1);

        registry = StatsdMeterRegistry.builder(configWithFlavor(StatsdFlavor.ETSY)).clock(clock).lineSink(line -> {
            if (line.contains("max")) {
                maxCount.countDown();
            }
        }).build();

        LongTaskTimer ltt = registry.more().longTaskTimer("ltt");
        ltt.start();
        registry.poll();

        assertThat(maxCount.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private UnicastProcessor<String> lineProcessor() {
        return UnicastProcessor.create(Queues.<String>unboundedMultiproducer().get());
    }

    private Consumer<String> toLineSink(Processor<String, String> lines) {
        return toLineSink(lines, 1);
    }

    private Consumer<String> toLineSink(Processor<String, String> lines, int numLines) {
        AtomicInteger latch = new AtomicInteger(numLines);
        return l -> {
            lines.onNext(l);
            if (latch.decrementAndGet() == 0) {
                lines.onComplete();
            }
        };
    }

}
