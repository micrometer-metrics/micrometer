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
package io.micrometer.dynatrace.v2;

import com.dynatrace.file.util.DynatraceFileBasedConfigurationProvider;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.lang.Double.*;
import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DynatraceExporterV2}.
 *
 * @author Georg Pirklbauer
 * @author Jonatan Ivanov
 */
class DynatraceExporterV2Test {

    private static final Map<String, String> SEEN_METADATA = new HashMap<>();

    private DynatraceConfig config;

    private MockClock clock;

    private HttpSender httpClient;

    private DynatraceMeterRegistry meterRegistry;

    private DynatraceExporterV2 exporter;

    @BeforeEach
    void setUp() {
        this.config = createDefaultDynatraceConfig();
        this.clock = new MockClock();
        // So that the Dynatrace library will not complain
        this.clock.add(System.currentTimeMillis(), MILLISECONDS);
        this.httpClient = mock(HttpSender.class);

        this.exporter = createExporter(httpClient);
        this.meterRegistry = DynatraceMeterRegistry.builder(config).clock(clock).httpClient(httpClient).build();

        SEEN_METADATA.clear();
    }

    @Test
    void toGaugeLine() {
        meterRegistry.gauge("my.gauge", 1.23);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        List<String> lines = exporter.toGaugeLine(gauge, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.gauge,dt.metrics.source=micrometer gauge,1.23 " + clock.wallTime());
    }

    @Test
    void toGaugeLineShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toGaugeLineShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge, SEEN_METADATA)).isEmpty();

        meterRegistry.gauge("my.gauge", NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toGaugeLineWithTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(2.3d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        List<String> lines = exporter.toGaugeLine(timeGauge, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.timeGauge,dt.metrics.source=micrometer gauge,2.3 " + clock.wallTime());
    }

    @Test
    void toGaugeLineWithTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();

        assertThat(exporter.toGaugeLine(timeGauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toGaugeLineWithTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toGaugeLine(timeGauge, SEEN_METADATA)).isEmpty();

        obj = new AtomicReference<>(NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toGaugeLine(timeGauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toCounterLine() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment();
        counter.increment();
        counter.increment();
        clock.add(config.step());

        List<String> lines = exporter.toCounterLine(counter, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.counter,dt.metrics.source=micrometer count,delta=3 " + clock.wallTime());
    }

    @Test
    void toCounterLineShouldDropNanValue() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(NaN);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toCounterLineShouldDropInfiniteValue() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toCounterLineWithFunctionCounter() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(2.3d);
        clock.add(config.step());

        List<String> lines = exporter.toCounterLine(functionCounter, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.functionCounter,dt.metrics.source=micrometer count,delta=2.3 " + clock.wallTime());
    }

    @Test
    void toCounterLineWithFunctionCounterShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(NaN);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(functionCounter, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toCounterLineWithFunctionCounterShouldDropInfiniteValue() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(functionCounter, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toTimerLine() {
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(Duration.ofMillis(60));
        timer.record(Duration.ofMillis(20));
        timer.record(Duration.ofMillis(10));
        clock.add(config.step());

        List<String> lines = exporter.toTimerLine(timer, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.timer,dt.metrics.source=micrometer gauge,min=10,max=60,sum=90,count=3 " + clock.wallTime());
    }

    @Test
    void toTimerLine_DropIfCountIsZero() {
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(Duration.ofMillis(60));
        clock.add(config.step());

        List<String> lines = exporter.toTimerLine(timer, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.timer,dt.metrics.source=micrometer gauge,min=60,max=60,sum=60,count=1 " + clock.wallTime());

        clock.add(config.step());
        // Before the update to drop zero count lines, this would contain 1 line (with
        // count=0), which is not desired.
        assertThat(exporter.toTimerLine(timer, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toFunctionTimerLineShouldDropNanTotal() {
        FunctionTimer functionTimer = new FunctionTimer() {
            @Override
            public double count() {
                return 500;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public double totalTime(TimeUnit unit) {
                return NaN;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public TimeUnit baseTimeUnit() {
                return MILLISECONDS;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public Id getId() {
                return new Id("my.functionTimer", Tags.empty(), null, null, Type.TIMER);
            }

        };

        assertThat(exporter.toFunctionTimerLine(functionTimer, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toFunctionTimerLine() {
        FunctionTimer functionTimer = new FunctionTimer() {
            @Override
            public double count() {
                return 500;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public double totalTime(TimeUnit unit) {
                return 5000;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public TimeUnit baseTimeUnit() {
                return MILLISECONDS;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public Id getId() {
                return new Id("my.functionTimer", Tags.empty(), null, null, Type.TIMER);
            }
        };

        List<String> lines = exporter.toFunctionTimerLine(functionTimer, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.functionTimer,dt.metrics.source=micrometer gauge,min=10,max=10,sum=5000,count=500 "
                    + clock.wallTime());
    }

    @Test
    void toLongTaskTimerLine() {
        LongTaskTimer longTaskTimer = LongTaskTimer.builder("my.longTaskTimer").register(meterRegistry);
        List<Integer> samples = Arrays.asList(48, 42, 40, 35, 22, 16, 13, 8, 6, 4, 2);
        int prior = samples.get(0);
        for (Integer value : samples) {
            clock.add(prior - value, SECONDS);
            longTaskTimer.start();
            prior = value;
        }
        clock(meterRegistry).add(samples.get(samples.size() - 1), SECONDS);

        List<String> lines = exporter.toLongTaskTimerLine(longTaskTimer, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.longTaskTimer,dt.metrics.source=micrometer gauge,min=2000,max=48000,sum=236000,count=11 "
                    + clock.wallTime());
    }

    @Issue("#3985")
    @Test
    void longTaskTimerWithSingleValueExportsConsistentData() throws InterruptedException {
        // In the past, there were problems with the LongTaskTimer: In cases where only
        // one value was exported, the max and duration were read sequentially while the
        // clock continued to tick in the background. Since the Dynatrace API checks this
        // data strictly, data where the sum and max were not exactly the same when the
        // count was 1 were rejected. The discrepancy is only caused by the non-atomicity
        // of retrieving max and sum. As soon as there is more than 1 observation, this
        // problem should disappear since the Dynatrace API checks for min <= mean <= max,
        // and that should always be the case when there is more than 1 value. If it is
        // not the case, the underlying data collection is really broken. For example, we
        // saw this issue with the metric 'http.server.requests.active', when there was
        // exactly one request in-flight. The retrieval of max and total are not
        // synchronized, so the clock continues to tick and results in two different
        // values (e.g., max=0.764418,sum=0.700539,count=1, which is invalid according to
        // the Dynatrace specification). Therefore, for this test we need to use the
        // system clock.
        DynatraceConfig config = createDefaultDynatraceConfig();
        Clock clock = Clock.SYSTEM;
        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(config).clock(clock).build();
        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);

        LongTaskTimer ltt = LongTaskTimer.builder("ltt").register(registry);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);
        executorService.submit(() -> ltt.record(sleeperTask(startLatch, stopLatch)));
        awaitSafely(startLatch); // wait till the ExecutorService schedules the task.
        Thread.sleep(50); // let it run a little
        List<String> lines = exporter.toLongTaskTimerLine(ltt, SEEN_METADATA).collect(Collectors.toList());
        stopLatch.countDown(); // stop the execution
        // export complete, can shut down active background thread.
        executorService.shutdownNow();

        assertThat(lines).hasSize(1);
        // ltt,dt.metrics.source=micrometer gauge,min=5,max=5,sum=5,count=1 1694133659649
        Matcher matcher = Pattern
            .compile("^.+,.+,min=(?<min>.+),max=(?<max>.+),sum=(?<sum>.+),count=(?<count>.+) \\d+$")
            .matcher(lines.get(0));
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group("min")).isEqualTo(matcher.group("sum")).isEqualTo(matcher.group("max"));
        assertThat(matcher.group("count")).isEqualTo("1");
    }

    @Issue("#3985")
    @Test
    void longTaskTimerWithMultipleValuesExportsConsistentData() throws InterruptedException {
        // For this test we need to use the system clock.
        // See longTaskTimerWithSingleValueExportsConsistentData for more info
        DynatraceConfig config = createDefaultDynatraceConfig();
        Clock clock = Clock.SYSTEM;
        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(config).clock(clock).build();
        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);

        LongTaskTimer ltt = LongTaskTimer.builder("ltt").register(registry);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch1 = new CountDownLatch(1);
        CountDownLatch startLatch2 = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);
        executorService.submit(() -> ltt.record(sleeperTask(startLatch1, stopLatch)));
        executorService.submit(() -> ltt.record(sleeperTask(startLatch2, stopLatch)));

        awaitSafely(startLatch1); // wait till the ExecutorService schedules task1.
        awaitSafely(startLatch2); // wait till the ExecutorService schedules task2.
        Thread.sleep(50); // let them run a little
        List<String> lines = exporter.toLongTaskTimerLine(ltt, SEEN_METADATA).collect(Collectors.toList());
        stopLatch.countDown(); // stop the execution of both tasks
        // export complete, can shut down active background thread.
        executorService.shutdownNow();

        // assertions
        assertThat(lines).hasSize(1);
        // ltt,dt.metrics.source=micrometer gauge,min=5,max=5,sum=5,count=1 1694133659649
        Matcher matcher = Pattern
            .compile("^.+,.+,min=(?<min>.+),max=(?<max>.+),sum=(?<sum>.+),count=(?<count>.+) \\d+$")
            .matcher(lines.get(0));
        assertThat(matcher.matches()).isTrue();
        double min = Double.parseDouble(matcher.group("min"));
        double max = Double.parseDouble(matcher.group("max"));
        double sum = Double.parseDouble(matcher.group("sum"));
        int count = Integer.parseInt(matcher.group("count"));
        double mean = sum / count;
        assertThat(min).isLessThanOrEqualTo(mean);
        assertThat(mean).isLessThanOrEqualTo(max);
        assertThat(count).isEqualTo(2);
    }

    /**
     * A task that blocks till you call {@link CountDownLatch#countDown()} on
     * {@code stopLatch}. It can also signal you that it started if you {@code await} on
     * {@code startLatch}.
     * @param startLatch The latch used to signal that the task has started.
     * @param stopLatch The latch used to signal that the task should stop.
     * @return a Runnable task
     */
    private Runnable sleeperTask(CountDownLatch startLatch, CountDownLatch stopLatch) {
        return () -> sleep(startLatch, stopLatch);
    }

    /**
     * Blocks till you call {@link CountDownLatch#countDown()} on {@code stopLatch}. It
     * can also signal you that it started if you {@code await} on {@code startLatch}.
     * @param startLatch The latch used to signal that the method was called.
     * @param stopLatch The latch used to signal that the method should terminate.
     */
    private void sleep(CountDownLatch startLatch, CountDownLatch stopLatch) {
        startLatch.countDown();
        awaitSafely(stopLatch);
    }

    private void awaitSafely(CountDownLatch latch) {
        try {
            if (!latch.await(1, SECONDS)) {
                throw new RuntimeException("Waiting on latch timed out!");
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testToDistributionSummaryLine() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        summary.record(2.3);
        summary.record(5.4);
        summary.record(0.1);
        clock.add(config.step());

        List<String> lines = exporter.toDistributionSummaryLine(summary, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.summary,dt.metrics.source=micrometer gauge,min=0.1,max=5.4,sum=10.9,count=4 " + clock.wallTime());
    }

    @Test
    void testToDistributionSummaryLine_DropsLineIfCountIsZero() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        clock.add(config.step());

        List<String> nonEmptyLines = exporter.toDistributionSummaryLine(summary, SEEN_METADATA)
            .collect(Collectors.toList());
        assertThat(nonEmptyLines).hasSize(1);
        assertThat(nonEmptyLines.get(0)).isEqualTo(
                "my.summary,dt.metrics.source=micrometer gauge,min=3.1,max=3.1,sum=3.1,count=1 " + clock.wallTime());

        clock.add(config.step());
        // Before the update to drop zero count lines, this would contain 1 line (with
        // count=0), which is not desired.
        assertThat(exporter.toDistributionSummaryLine(summary, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toGaugeLineWithMeter() {
        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 42d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.VALUE);
        Meter meter = Meter.builder("my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(meterRegistry);

        List<String> lines = exporter.toGaugeLine(meter, SEEN_METADATA).collect(Collectors.toList());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,23 " + clock.wallTime());
        assertThat(lines.get(1)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,42 " + clock.wallTime());
        assertThat(lines.get(2)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,5 " + clock.wallTime());
    }

    @Test
    void gaugeWithInvalidNameShouldBeDropped() {
        meterRegistry.gauge("", 1.23);
        Gauge gauge = meterRegistry.find("").gauge();
        assertThat(gauge).isNotNull();
        assertThat(exporter.toGaugeLine(gauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toGaugeLineShouldContainTags() {
        List<String> expectedDims = Arrays.asList("tag1=value1", "tag2=value2", "dt.metrics.source=micrometer");

        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();

        List<String> lines = exporter.toGaugeLine(gauge, SEEN_METADATA).collect(Collectors.toList());

        assertThat(lines).hasSize(1).first().satisfies(line -> {
            assertThat(extractBase(line)).isEqualTo("my.gauge gauge,1.23 " + clock.wallTime());
            assertThat(extractDims(line)).containsExactlyInAnyOrderElementsOf(expectedDims);
        });
    }

    @Test
    void toGaugeLineShouldOmitBlankTagValues() {
        List<String> expectedDims = Arrays.asList("tag1=value1", "dt.metrics.source=micrometer");

        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();

        List<String> lines = exporter.toGaugeLine(gauge, SEEN_METADATA).collect(Collectors.toList());

        assertThat(lines).hasSize(1).first().satisfies(line -> {
            assertThat(extractBase(line)).isEqualTo("my.gauge gauge,1.23 " + clock.wallTime());
            assertThat(extractDims(line)).containsExactlyInAnyOrderElementsOf(expectedDims);
        });
    }

    @Test
    void counterWithInvalidNameShouldBeDropped() {
        meterRegistry.counter("");
        Counter counter = meterRegistry.find("").counter();
        assertThat(counter).isNotNull();
        assertThat(exporter.toCounterLine(counter, SEEN_METADATA)).isEmpty();
    }

    @Test
    void toCounterLineShouldContainTags() {
        List<String> expectedDims = Arrays.asList("tag1=value1", "tag2=value2", "dt.metrics.source=micrometer");

        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        List<String> lines = exporter.toCounterLine(counter, SEEN_METADATA).collect(Collectors.toList());

        assertThat(lines).hasSize(1).first().satisfies(line -> {
            assertThat(extractBase(line)).isEqualTo("my.counter count,delta=0 " + clock.wallTime());
            assertThat(extractDims(line)).containsExactlyInAnyOrderElementsOf(expectedDims);
        });
    }

    @Test
    void toCounterLineShouldOmitBlankTagValues() {
        List<String> expectedDims = Arrays.asList("tag1=value1", "dt.metrics.source=micrometer");

        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        List<String> lines = exporter.toCounterLine(counter, SEEN_METADATA).collect(Collectors.toList());

        assertThat(lines).hasSize(1).first().satisfies(line -> {
            assertThat(extractBase(line)).isEqualTo("my.counter count,delta=0 " + clock.wallTime());
            assertThat(extractDims(line)).containsExactlyInAnyOrderElementsOf(expectedDims);
        });
    }

    @Test
    void linesExceedingLengthLimitDiscardedGracefully() {
        List<Tag> tagList = new ArrayList<>();
        for (int i = 0; i < 3300; i++) {
            tagList.add(Tag.of(String.format("key%d", i), String.format("val%d", i)));
        }

        meterRegistry.gauge("serialized.as.too.long.line", tagList, 1.23);
        Gauge gauge = meterRegistry.find("serialized.as.too.long.line").gauge();
        assertThat(gauge).isNotNull();

        assertThat(exporter.toGaugeLine(gauge, SEEN_METADATA)).isEmpty();
    }

    @Test
    void shouldSendHeadersAndBody() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class)))
            .thenReturn(new HttpSender.Response(202, "{ \"linesOk\": 3, \"linesInvalid\": 0, \"error\": null }"));

        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(12d);
        meterRegistry.gauge("my.gauge", 42d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(22, MILLISECONDS);
        clock.add(config.step());

        exporter.export(Arrays.asList(counter, gauge, timer));

        verify(httpClient).send(assertArg(request -> {
            assertThat(request.getRequestHeaders()).containsOnly(entry("Content-Type", "text/plain"),
                    entry("User-Agent", "micrometer"), entry("Authorization", "Api-Token apiToken"));
            assertThat(request.getEntity()).asString()
                .hasLineCount(4)
                .containsSubsequence("my.counter,dt.metrics.source=micrometer count,delta=12 " + clock.wallTime(),
                        "my.gauge,dt.metrics.source=micrometer gauge,42 " + clock.wallTime(),
                        "my.timer,dt.metrics.source=micrometer gauge,min=22,max=22,sum=22,count=1 " + clock.wallTime(),
                        "#my.timer gauge dt.meta.unit=ms");
        }));
    }

    @Test
    void endpointPickedUpBetweenExportsAndChangedPropertiesFile() throws Throwable {
        String randomUuid = UUID.randomUUID().toString();
        final Path tempFile = Files.createTempFile(randomUuid, ".properties");

        DynatraceConfig config = new DynatraceConfig() {
            // if nothing is set for uri and token, read from the file watcher
            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }

            @Nullable
            @Override
            public String get(String key) {
                return null;
            }
        };

        // set up mocks
        HttpSender httpSender = mock(HttpSender.class);
        MockClock clock = new MockClock();
        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpSender);
        DynatraceMeterRegistry meterRegistry = DynatraceMeterRegistry.builder(config)
            .httpClient(httpSender)
            .clock(clock)
            .build();

        when(httpSender.post(anyString())).thenCallRealMethod();
        when(httpSender.newRequest(anyString())).thenCallRealMethod();
        when(httpSender.send(any())).thenReturn(new HttpSender.Response(202, "{\n" + "  \"linesOk\": 1,\n"
                + "  \"linesInvalid\": 0,\n" + "  \"error\": null,\n" + "  \"warnings\": null\n" + "}"));

        // fill the file with content
        final String baseUri = "https://your-dynatrace-ingest-url/api/v2/metrics/ingest/";
        final String firstUri = baseUri + "first";
        final String secondUri = baseUri + "second";

        Files.write(tempFile, ("DT_METRICS_INGEST_URL = " + firstUri + "\n"
                + "DT_METRICS_INGEST_API_TOKEN = YOUR.DYNATRACE.TOKEN.FIRST")
            .getBytes());

        DynatraceFileBasedConfigurationProvider.getInstance()
            .forceOverwriteConfig(tempFile.toString(), Duration.ofMillis(50));
        await().atMost(1_000, MILLISECONDS).until(() -> config.uri().equals(firstUri));
        Counter counter = meterRegistry.counter("test.counter");
        counter.increment(10);
        clock.add(config.step());
        exporter.export(Collections.singletonList(counter));

        ArgumentCaptor<HttpSender.Request> firstRequestCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpSender, times(1)).send(firstRequestCaptor.capture());
        HttpSender.Request firstRequest = firstRequestCaptor.getValue();

        assertThat(firstRequest.getUrl()).hasToString(firstUri);
        assertThat(firstRequest.getRequestHeaders()).containsOnly(entry("Content-Type", "text/plain"),
                entry("User-Agent", "micrometer"), entry("Authorization", "Api-Token YOUR.DYNATRACE.TOKEN.FIRST"));
        assertThat(firstRequest.getEntity()).asString()
            .isEqualTo("test.counter,dt.metrics.source=micrometer count,delta=10");

        counter.increment(30);
        clock.add(config.step());

        // overwrite the file content to use the second uri
        Files.write(tempFile, ("DT_METRICS_INGEST_URL = " + secondUri + "\n"
                + "DT_METRICS_INGEST_API_TOKEN = YOUR.DYNATRACE.TOKEN.SECOND")
            .getBytes());

        await().atMost(1_000, MILLISECONDS).until(() -> config.uri().equals(secondUri));
        exporter.export(Collections.singletonList(counter));

        ArgumentCaptor<HttpSender.Request> secondRequestCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpSender, times(2)).send(secondRequestCaptor.capture());
        HttpSender.Request secondRequest = secondRequestCaptor.getValue();

        assertThat(secondRequest.getUrl()).hasToString(secondUri);
        assertThat(secondRequest.getRequestHeaders()).containsOnly(entry("Content-Type", "text/plain"),
                entry("User-Agent", "micrometer"), entry("Authorization", "Api-Token YOUR.DYNATRACE.TOKEN.SECOND"));
        assertThat(secondRequest.getEntity()).asString()
            .isEqualTo("test.counter,dt.metrics.source=micrometer count,delta=30");
    }

    @Test
    void gaugeMetadataIsSerialized() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        Gauge.builder("my.gauge", () -> 1.23).description("my.description").baseUnit("Liters").register(meterRegistry);
        exporter.export(meterRegistry.getMeters());

        verify(builder).withPlainText(assertArg(body -> {
            // get the data set to the request and split it into lines on the newline
            // char.
            assertThat(body.split("\n")).containsExactly(
                    "my.gauge,dt.metrics.source=micrometer gauge,1.23 " + clock.wallTime(),
                    "#my.gauge gauge dt.meta.description=my.description,dt.meta.unit=Liters");
        }));
    }

    @Test
    void counterMetadataIsSerialized() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        Counter counter = Counter.builder("my.count")
            .description("count description")
            .baseUnit("Bytes")
            .register(meterRegistry);
        counter.increment(5.234);
        clock.add(config.step());
        exporter.export(meterRegistry.getMeters());

        verify(builder).withPlainText(assertArg(body -> {
            assertThat(body.split("\n")).containsExactly(
                    "my.count,dt.metrics.source=micrometer count,delta=5.234 " + clock.wallTime(),
                    "#my.count count dt.meta.description=count\\ description,dt.meta.unit=Bytes");
        }));
    }

    @Test
    void shouldAddMetadataOnlyWhenUnitOrDescriptionIsPresent() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        Gauge.builder("gauge", () -> 10.00).register(meterRegistry);
        Gauge.builder("gauge.d", () -> 20.00).description("temperature").register(meterRegistry);
        Gauge.builder("gauge.u", () -> 30.00).baseUnit("kelvin").register(meterRegistry);
        Gauge.builder("gauge.du", () -> 40.00).description("temperature").baseUnit("kelvin").register(meterRegistry);
        exporter.export(meterRegistry.getMeters());

        verify(builder).withPlainText(assertArg(body -> assertThat(body.split("\n")).containsExactlyInAnyOrder(
                "gauge,dt.metrics.source=micrometer gauge,10 " + clock.wallTime(),
                // no metadata since no unit nor description
                "gauge.d,dt.metrics.source=micrometer gauge,20 " + clock.wallTime(),
                "#gauge.d gauge dt.meta.description=temperature",
                "gauge.u,dt.metrics.source=micrometer gauge,30 " + clock.wallTime(),
                "#gauge.u gauge dt.meta.unit=kelvin",
                "gauge.du,dt.metrics.source=micrometer gauge,40 " + clock.wallTime(),
                "#gauge.du gauge dt.meta.description=temperature,dt.meta.unit=kelvin")));
    }

    @Test
    void metersShouldUseUcumCompliantUnits() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        meterRegistry.timer("test.timer").record(Duration.ofMillis(12));
        DistributionSummary.builder("test.summary").baseUnit("days").register(meterRegistry).record(42.0);
        meterRegistry.more().timeGauge("test.timegauge", Tags.empty(), this, TimeUnit.MICROSECONDS, x -> 1_000);
        FunctionTimer.builder("test.ft", this, x -> 1, x -> 100, MILLISECONDS).register(meterRegistry);
        Counter.builder("test.seconds").baseUnit("seconds").register(meterRegistry).increment(10);
        FunctionCounter.builder("test.fc", this, x -> 1_000_000).baseUnit("ns").register(meterRegistry);

        Sample sample = meterRegistry.more().longTaskTimer("test.ltt").start();
        clock.add(config.step().plus(Duration.ofSeconds(2)));

        exporter.export(meterRegistry.getMeters());
        sample.stop();

        verify(builder).withPlainText(assertArg(body -> assertThat(body.split("\n")).containsExactlyInAnyOrder(
                "test.timer,dt.metrics.source=micrometer gauge,min=12,max=12,sum=12,count=1 " + clock.wallTime(),
                "#test.timer gauge dt.meta.unit=ms",
                "test.summary,dt.metrics.source=micrometer gauge,min=42,max=42,sum=42,count=1 " + clock.wallTime(),
                "#test.summary gauge dt.meta.unit=days",
                "test.timegauge,dt.metrics.source=micrometer gauge,1 " + clock.wallTime(),
                "#test.timegauge gauge dt.meta.unit=ms",
                "test.ft,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 " + clock.wallTime(),
                "#test.ft gauge dt.meta.unit=ms",
                "test.seconds,dt.metrics.source=micrometer count,delta=10 " + clock.wallTime(),
                "#test.seconds count dt.meta.unit=s",
                "test.fc,dt.metrics.source=micrometer count,delta=1000000 " + clock.wallTime(),
                "#test.fc count dt.meta.unit=ns",
                "test.ltt,dt.metrics.source=micrometer gauge,min=62000,max=62000,sum=62000,count=1 " + clock.wallTime(),
                "#test.ltt gauge dt.meta.unit=ms")));
    }

    @Test
    void userDefinedUnitsShouldBeFormattedToUcumCompliantUnits() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        Counter.builder("test.tu.nanos").baseUnit(NANOSECONDS.toString()).register(meterRegistry).increment(1);
        Counter.builder("test.nanoseconds").baseUnit("nanoseconds").register(meterRegistry).increment(2);
        Counter.builder("test.nanosecond").baseUnit("nanosecond").register(meterRegistry).increment(3);

        Counter.builder("test.tu.micros").baseUnit(MICROSECONDS.toString()).register(meterRegistry).increment(4);
        Counter.builder("test.microseconds").baseUnit("microseconds").register(meterRegistry).increment(5);
        Counter.builder("test.microsecond").baseUnit("microsecond").register(meterRegistry).increment(6);

        Counter.builder("test.tu.millis").baseUnit(MILLISECONDS.toString()).register(meterRegistry).increment(7);
        Counter.builder("test.milliseconds").baseUnit("milliseconds").register(meterRegistry).increment(8);
        Counter.builder("test.millisecond").baseUnit("millisecond").register(meterRegistry).increment(9);

        Counter.builder("test.tu.seconds").baseUnit(SECONDS.toString()).register(meterRegistry).increment(10);
        Counter.builder("test.seconds").baseUnit("seconds").register(meterRegistry).increment(11);
        Counter.builder("test.second").baseUnit("second").register(meterRegistry).increment(12);

        Counter.builder("test.tu.minutes").baseUnit(MINUTES.toString()).register(meterRegistry).increment(13);
        Counter.builder("test.minutes").baseUnit("minutes").register(meterRegistry).increment(14);
        Counter.builder("test.minute").baseUnit("minute").register(meterRegistry).increment(15);

        Counter.builder("test.tu.hours").baseUnit(HOURS.toString()).register(meterRegistry).increment(16);
        Counter.builder("test.hours").baseUnit("hours").register(meterRegistry).increment(17);
        Counter.builder("test.hour").baseUnit("hour").register(meterRegistry).increment(18);

        clock.add(config.step().plus(Duration.ofSeconds(2)));
        exporter.export(meterRegistry.getMeters());

        verify(builder).withPlainText(assertArg(body -> assertThat(body.split("\n")).containsExactlyInAnyOrder(
                "test.tu.nanos,dt.metrics.source=micrometer count,delta=1 " + clock.wallTime(),
                "#test.tu.nanos count dt.meta.unit=ns",
                "test.nanoseconds,dt.metrics.source=micrometer count,delta=2 " + clock.wallTime(),
                "#test.nanoseconds count dt.meta.unit=ns",
                "test.nanosecond,dt.metrics.source=micrometer count,delta=3 " + clock.wallTime(),
                "#test.nanosecond count dt.meta.unit=ns",

                "test.tu.micros,dt.metrics.source=micrometer count,delta=4 " + clock.wallTime(),
                "#test.tu.micros count dt.meta.unit=us",
                "test.microseconds,dt.metrics.source=micrometer count,delta=5 " + clock.wallTime(),
                "#test.microseconds count dt.meta.unit=us",
                "test.microsecond,dt.metrics.source=micrometer count,delta=6 " + clock.wallTime(),
                "#test.microsecond count dt.meta.unit=us",

                "test.tu.millis,dt.metrics.source=micrometer count,delta=7 " + clock.wallTime(),
                "#test.tu.millis count dt.meta.unit=ms",
                "test.milliseconds,dt.metrics.source=micrometer count,delta=8 " + clock.wallTime(),
                "#test.milliseconds count dt.meta.unit=ms",
                "test.millisecond,dt.metrics.source=micrometer count,delta=9 " + clock.wallTime(),
                "#test.millisecond count dt.meta.unit=ms",

                "test.tu.seconds,dt.metrics.source=micrometer count,delta=10 " + clock.wallTime(),
                "#test.tu.seconds count dt.meta.unit=s",
                "test.seconds,dt.metrics.source=micrometer count,delta=11 " + clock.wallTime(),
                "#test.seconds count dt.meta.unit=s",
                "test.second,dt.metrics.source=micrometer count,delta=12 " + clock.wallTime(),
                "#test.second count dt.meta.unit=s",

                "test.tu.minutes,dt.metrics.source=micrometer count,delta=13 " + clock.wallTime(),
                "#test.tu.minutes count dt.meta.unit=min",
                "test.minutes,dt.metrics.source=micrometer count,delta=14 " + clock.wallTime(),
                "#test.minutes count dt.meta.unit=min",
                "test.minute,dt.metrics.source=micrometer count,delta=15 " + clock.wallTime(),
                "#test.minute count dt.meta.unit=min",

                "test.tu.hours,dt.metrics.source=micrometer count,delta=16 " + clock.wallTime(),
                "#test.tu.hours count dt.meta.unit=h",
                "test.hours,dt.metrics.source=micrometer count,delta=17 " + clock.wallTime(),
                "#test.hours count dt.meta.unit=h",
                "test.hour,dt.metrics.source=micrometer count,delta=18 " + clock.wallTime(),
                "#test.hour count dt.meta.unit=h")));
    }

    @Test
    void sendsTwoRequestsWhenSizeLimitIsReachedWithMetadata() {
        HttpSender.Request.Builder firstReq = spy(HttpSender.Request.build(config.uri(), httpClient));
        HttpSender.Request.Builder secondReq = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(firstReq).thenReturn(secondReq);

        // create a dynatrace config (same as the one returned by
        // createDefaultDynatraceConfig() but with a batch size of 3).
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String uri() {
                return "http://localhost";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String apiToken() {
                return "apiToken";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }

            @Override
            public int batchSize() {
                return 3;
            }
        };

        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);
        DynatraceMeterRegistry meterRegistry = DynatraceMeterRegistry.builder(config)
            .httpClient(httpClient)
            .clock(clock)
            .build();

        Counter counter = Counter.builder("my.count")
            .description("count description")
            .baseUnit("Bytes")
            .register(meterRegistry);
        counter.increment(5.234);
        Gauge.builder("my.gauge", () -> 1.23).description("my.description").baseUnit("Liters").register(meterRegistry);
        clock.add(config.step());
        exporter.export(meterRegistry.getMeters());

        ArgumentCaptor<String> firstReqCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> secondReqCap = ArgumentCaptor.forClass(String.class);
        verify(firstReq).withPlainText(firstReqCap.capture());
        verify(secondReq).withPlainText(secondReqCap.capture());

        String[] firstReqLines = firstReqCap.getValue().split("\n");
        String[] secondReqLines = secondReqCap.getValue().split("\n");

        // the first request will contain the metric lines
        assertThat(firstReqLines).containsExactly(
                "my.count,dt.metrics.source=micrometer count,delta=5.234 " + clock.wallTime(),
                "my.gauge,dt.metrics.source=micrometer gauge,1.23 " + clock.wallTime(),
                "#my.count count dt.meta.description=count\\ description,dt.meta.unit=Bytes");

        // the second request will contain the leftover metadata line
        assertThat(secondReqLines)
            .containsExactly("#my.gauge gauge dt.meta.description=my.description,dt.meta.unit=Liters");
    }

    @Test
    void metadataIsSerializedOnceWhenSetTwice() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        // both counters have the same unit and description, but other tags differ
        Counter counter1 = Counter.builder("my.count")
            .description("count description")
            .baseUnit("Bytes")
            .tag("counter-number", "counter1")
            .register(meterRegistry);
        Counter counter2 = Counter.builder("my.count")
            .description("count description")
            .baseUnit("Bytes")
            .tag("counter-number", "counter2")
            .register(meterRegistry);

        counter1.increment(5.234);
        counter2.increment(2.345);
        clock.add(config.step());
        exporter.export(meterRegistry.getMeters());

        verify(builder).withPlainText(assertArg(body -> {
            assertThat(body.split("\n")).containsExactly(
                    "my.count,dt.metrics.source=micrometer,counter-number=counter1 count,delta=5.234 "
                            + clock.wallTime(),
                    "my.count,dt.metrics.source=micrometer,counter-number=counter2 count,delta=2.345 "
                            + clock.wallTime(),
                    "#my.count count dt.meta.description=count\\ description,dt.meta.unit=Bytes");
        }));
    }

    @Test
    void conflictingMetadataIsIgnored() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        // the unit and description are different between counters, while the name stays
        // the same.
        Counter counter1 = Counter.builder("my.count")
            .description("count 1 description")
            .baseUnit("Bytes")
            .tag("counter-number", "counter1")
            .register(meterRegistry);
        Counter counter2 = Counter.builder("my.count")
            .description("count description")
            .baseUnit("not Bytes")
            .tag("counter-number", "counter2")
            .register(meterRegistry);

        counter1.increment(5.234);
        counter2.increment(2.345);
        clock.add(config.step());
        exporter.export(meterRegistry.getMeters());

        Iterator<List<String>> expectedDims = Arrays
            .asList(Arrays.asList("counter-number=counter1", "dt.metrics.source=micrometer"),
                    Arrays.asList("counter-number=counter2", "dt.metrics.source=micrometer"))
            .iterator();
        Iterator<String> expectedBases = Arrays
            .asList("my.count count,delta=5.234 " + clock.wallTime(), "my.count count,delta=2.345 " + clock.wallTime())
            .iterator();

        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).withPlainText(stringArgumentCaptor.capture());
        List<String> lines = Arrays.asList(stringArgumentCaptor.getValue().split("\n"));

        assertThat(lines).hasSize(2).allSatisfy(line -> {
            assertThat(extractBase(line)).isEqualTo(expectedBases.next());
            assertThat(extractDims(line)).containsExactlyInAnyOrderElementsOf(expectedDims.next());
        });
    }

    @Test
    void metadataIsNotExportedWhenTurnedOff() {
        HttpSender.Request.Builder builder = spy(HttpSender.Request.build(config.uri(), httpClient));
        when(httpClient.post(anyString())).thenReturn(builder);

        // create a dynatrace config (same as the one returned by
        // createDefaultDynatraceConfig() but with metadata turned off).
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String uri() {
                return "http://localhost";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String apiToken() {
                return "apiToken";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }

            @Override
            public boolean exportMeterMetadata() {
                return false;
            }
        };

        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);
        DynatraceMeterRegistry meterRegistry = DynatraceMeterRegistry.builder(config)
            .httpClient(httpClient)
            .clock(clock)
            .build();

        Counter counter = Counter.builder("my.count")
            .description("count description")
            .baseUnit("Bytes")
            .register(meterRegistry);
        counter.increment(5.234);
        clock.add(config.step());
        exporter.export(meterRegistry.getMeters());

        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).withPlainText(stringArgumentCaptor.capture());
        List<String> lines = Arrays.asList(stringArgumentCaptor.getValue().split("\n"));

        assertThat(lines).hasSize(1)
            .containsExactly("my.count,dt.metrics.source=micrometer count,delta=5.234 " + clock.wallTime());
    }

    private DynatraceExporterV2 createExporter(HttpSender httpClient) {
        return new DynatraceExporterV2(config, clock, httpClient);
    }

    private DynatraceConfig createDefaultDynatraceConfig() {
        return new DynatraceConfig() {
            @Override
            @SuppressWarnings("NullableProblems")
            public String get(String key) {
                return null;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String uri() {
                return "http://localhost";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public String apiToken() {
                return "apiToken";
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }
        };
    }

    private String extractBase(String line) {
        if (line.startsWith("#"))
            return String.join(" ", Arrays.copyOfRange(line.split(" ", 3), 0, 2));
        return line.split(",", 2)[0] + " " + line.split(" ")[1]
                + (line.split(" ").length == 3 ? " " + line.split(" ")[2] : "");
    }

    private List<String> extractDims(String line) {
        if (line.startsWith("#"))
            return Arrays.asList(line.split(" ", 3)[2].split(","));
        return Arrays.asList(line.split(",", 2)[1].split(" ")[0].split(","));
    }

}
