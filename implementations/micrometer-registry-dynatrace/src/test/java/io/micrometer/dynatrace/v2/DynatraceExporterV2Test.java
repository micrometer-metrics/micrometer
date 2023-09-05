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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.LogEvent;
import io.micrometer.core.util.internal.logging.MockLogger;
import io.micrometer.core.util.internal.logging.MockLoggerFactory;
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR;
import static java.lang.Double.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
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

    private static final MockLoggerFactory FACTORY = new MockLoggerFactory();

    private static final MockLogger LOGGER = FACTORY.getLogger(DynatraceExporterV2.class);

    private DynatraceConfig config;

    private MockClock clock;

    private HttpSender httpClient;

    private DynatraceMeterRegistry meterRegistry;

    private DynatraceExporterV2 exporter;

    @BeforeEach
    void setUp() {
        this.config = createDefaultDynatraceConfig();
        this.clock = new MockClock();
        this.clock.add(System.currentTimeMillis(), MILLISECONDS); // Set the clock to
                                                                  // something recent so
                                                                  // that the Dynatrace
                                                                  // library will not
                                                                  // complain.
        this.httpClient = mock(HttpSender.class);
        this.exporter = FACTORY.injectLogger(() -> createExporter(httpClient));

        this.meterRegistry = DynatraceMeterRegistry.builder(config).clock(clock).httpClient(httpClient).build();
    }

    @AfterEach
    void tearDown() {
        LOGGER.clear();
    }

    @Test
    void toGaugeLine() {
        meterRegistry.gauge("my.gauge", 1.23);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        List<String> lines = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.gauge,dt.metrics.source=micrometer gauge,1.23 " + clock.wallTime());
    }

    @Test
    void toGaugeLineShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge)).isEmpty();
    }

    @Test
    void toGaugeLineShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge)).isEmpty();

        meterRegistry.gauge("my.gauge", NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge)).isEmpty();
    }

    @Test
    void toTimeGaugeLine() {
        AtomicReference<Double> obj = new AtomicReference<>(2.3d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        List<String> lines = exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.timeGauge,dt.metrics.source=micrometer gauge,2.3 " + clock.wallTime());
    }

    @Test
    void toTimeGaugeLineShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();

        assertThat(exporter.toTimeGaugeLine(timeGauge)).isEmpty();
    }

    @Test
    void toTimeGaugeLineShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toTimeGaugeLine(timeGauge)).isEmpty();

        obj = new AtomicReference<>(NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, MILLISECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toTimeGaugeLine(timeGauge)).isEmpty();
    }

    @Test
    void toCounterLine() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment();
        counter.increment();
        counter.increment();
        clock.add(config.step());

        List<String> lines = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.counter,dt.metrics.source=micrometer count,delta=3.0 " + clock.wallTime());
    }

    @Test
    void toCounterLineShouldDropNanValue() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(NaN);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter)).isEmpty();
    }

    @Test
    void toCounterLineShouldDropInfiniteValue() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter)).isEmpty();
    }

    @Test
    void toFunctionCounterLine() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(2.3d);
        clock.add(config.step());

        List<String> lines = exporter.toFunctionCounterLine(functionCounter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.functionCounter,dt.metrics.source=micrometer count,delta=2.3 " + clock.wallTime());
    }

    @Test
    void toFunctionCounterLineShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(NaN);
        clock.add(config.step());

        assertThat(exporter.toFunctionCounterLine(functionCounter)).isEmpty();
    }

    @Test
    void toFunctionCounterLineShouldDropInfiniteValue() {
        AtomicReference<Double> obj = new AtomicReference<>(0.0d);
        FunctionCounter.builder("my.functionCounter", obj, AtomicReference::get).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertThat(functionCounter).isNotNull();

        obj.set(POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toFunctionCounterLine(functionCounter)).isEmpty();
    }

    @Test
    void toTimerLine() {
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(Duration.ofMillis(60));
        timer.record(Duration.ofMillis(20));
        timer.record(Duration.ofMillis(10));
        clock.add(config.step());

        List<String> lines = exporter.toTimerLine(timer).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.timer,dt.metrics.source=micrometer gauge,min=10.0,max=60.0,sum=90.0,count=3 " + clock.wallTime());
    }

    @Test
    void toTimerLine_DropIfCountIsZero() {
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(Duration.ofMillis(60));
        clock.add(config.step());

        List<String> lines = exporter.toTimerLine(timer).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.timer,dt.metrics.source=micrometer gauge,min=60.0,max=60.0,sum=60.0,count=1 " + clock.wallTime());

        clock.add(config.step());
        // Before the update to drop zero count lines, this would contain 1 line (with
        // count=0), which is not desired.
        assertThat(exporter.toTimerLine(timer)).isEmpty();
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

        assertThat(exporter.toFunctionTimerLine(functionTimer)).isEmpty();
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

        List<String> lines = exporter.toFunctionTimerLine(functionTimer).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.functionTimer,dt.metrics.source=micrometer gauge,min=10.0,max=10.0,sum=5000.0,count=500 "
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

        List<String> lines = exporter.toLongTaskTimerLine(longTaskTimer).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.longTaskTimer,dt.metrics.source=micrometer gauge,min=2000.0,max=48000.0,sum=236000.0,count=11 "
                        + clock.wallTime());
    }

    private static void busyWait(LongTaskTimer ltt) {
        ltt.record(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(200);
                }
                catch (InterruptedException ignored) {
                }
            }
        });
    }

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
        Clock clock = Clock.SYSTEM;
        DynatraceConfig config = createDefaultDynatraceConfig();
        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(config).clock(clock).build();
        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);

        LongTaskTimer ltt = LongTaskTimer.builder("my.ltt").register(registry);

        // This will run in the background until it is interrupted down below.
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> busyWait(ltt));

        // wait for a little bit to ensure there is something to measure
        Thread.sleep(500);

        List<String> lines = exporter.toLongTaskTimerLine(ltt).collect(Collectors.toList());

        // export complete, can shut down active background thread.
        executorService.shutdownNow();

        // assertions
        assertThat(lines).hasSize(1);

        // A String[]: "min=1", "max=1", "sum=1", "count=1"
        String[] gaugeComponents = lines.get(0).split(" ")[1].split(",");
        // trim the "min=", "max=", etc. prefixes and keep only the string representations
        // of the values.
        String min = gaugeComponents[1].split("=")[1];
        String max = gaugeComponents[2].split("=")[1];
        String sum = gaugeComponents[3].split("=")[1];
        String count = gaugeComponents[4].split("=")[1];

        assertThat(min).isEqualTo(sum).isEqualTo(max);

        assertThat(count).isEqualTo("1");
    }

    @Test
    void longTaskTimerWithMultipleValuesExportsConsistentData() throws InterruptedException {
        // For this test we need to use the system clock. See
        // longTaskTimerWithSingleValueExportsConsistentData for
        // more info
        Clock clock = Clock.SYSTEM;
        DynatraceConfig config = createDefaultDynatraceConfig();
        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(config).clock(clock).build();
        DynatraceExporterV2 exporter = new DynatraceExporterV2(config, clock, httpClient);

        LongTaskTimer ltt = LongTaskTimer.builder("my.ltt").register(registry);

        // This will run in the background until it is interrupted down below.
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.submit(() -> busyWait(ltt));
        Thread.sleep(500);
        executorService.submit(() -> busyWait(ltt));

        Thread.sleep(500);

        List<String> lines = exporter.toLongTaskTimerLine(ltt).collect(Collectors.toList());

        // export complete, can shut down active background thread.
        executorService.shutdownNow();

        // assertions
        assertThat(lines).hasSize(1);

        // A String[]: "min=x", "max=x", "sum=x", "count=x"
        String[] gaugeComponents = lines.get(0).split(" ")[1].split(",");

        // trim the "min=", "max=", etc. prefixes and keep only the string representations
        // of the values.
        double min = Double.parseDouble(gaugeComponents[1].split("=")[1]);
        double max = Double.parseDouble(gaugeComponents[2].split("=")[1]);
        double sum = Double.parseDouble(gaugeComponents[3].split("=")[1]);
        int count = Integer.parseInt(gaugeComponents[4].split("=")[1]);
        double mean = sum / count;

        assertThat(min).isLessThanOrEqualTo(mean);
        assertThat(mean).isLessThanOrEqualTo(max);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testToDistributionSummaryLine() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        summary.record(2.3);
        summary.record(5.4);
        summary.record(0.1);
        clock.add(config.step());

        List<String> lines = exporter.toDistributionSummaryLine(summary).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.summary,dt.metrics.source=micrometer gauge,min=0.1,max=5.4,sum=10.9,count=4 " + clock.wallTime());
    }

    @Test
    void testToDistributionSummaryLine_DropsLineIfCountIsZero() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        clock.add(config.step());

        List<String> nonEmptyLines = exporter.toDistributionSummaryLine(summary).collect(Collectors.toList());
        assertThat(nonEmptyLines).hasSize(1);
        assertThat(nonEmptyLines.get(0)).isEqualTo(
                "my.summary,dt.metrics.source=micrometer gauge,min=3.1,max=3.1,sum=3.1,count=1 " + clock.wallTime());

        clock.add(config.step());
        // Before the update to drop zero count lines, this would contain 1 line (with
        // count=0), which is not desired.
        assertThat(exporter.toDistributionSummaryLine(summary)).isEmpty();
    }

    @Test
    void toMeterLine() {
        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 42d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.VALUE);
        Meter meter = Meter.builder("my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(meterRegistry);

        List<String> lines = exporter.toMeterLine(meter).collect(Collectors.toList());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,23.0 " + clock.wallTime());
        assertThat(lines.get(1)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,42.0 " + clock.wallTime());
        assertThat(lines.get(2)).isEqualTo("my.custom,dt.metrics.source=micrometer gauge,5.0 " + clock.wallTime());
    }

    @Test
    void gaugeWithInvalidNameShouldBeDropped() {
        meterRegistry.gauge("", 1.23);
        Gauge gauge = meterRegistry.find("").gauge();
        assertThat(gauge).isNotNull();
        assertThat(exporter.toGaugeLine(gauge)).isEmpty();
    }

    @Test
    void toGaugeLineShouldContainTags() {
        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();

        List<String> lines = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.gauge,tag1=value1,dt.metrics.source=micrometer,tag2=value2 gauge,1.23 " + clock.wallTime());
    }

    @Test
    void toGaugeLineShouldExportBlankTagValues() {
        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();

        List<String> lines = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.gauge,tag1=value1,dt.metrics.source=micrometer,tag2= gauge,1.23 " + clock.wallTime());
    }

    @Test
    void counterWithInvalidNameShouldBeDropped() {
        meterRegistry.counter("");
        Counter counter = meterRegistry.find("").counter();
        assertThat(counter).isNotNull();
        assertThat(exporter.toCounterLine(counter)).isEmpty();
    }

    @Test
    void toCounterLineShouldContainTags() {
        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        List<String> lines = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo(
                "my.counter,tag1=value1,dt.metrics.source=micrometer,tag2=value2 count,delta=0.0 " + clock.wallTime());
    }

    @Test
    void toCounterLineShouldExportBlankTagValues() {
        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        List<String> lines = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .isEqualTo("my.counter,tag1=value1,dt.metrics.source=micrometer,tag2= count,delta=0.0 " + clock.wallTime());
    }

    @Test
    void linesExceedingLengthLimitDiscardedGracefully() {
        List<Tag> tagList = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            tagList.add(Tag.of(String.format("key%d", i), String.format("val%d", i)));
        }
        Tags tags = Tags.concat(tagList);

        meterRegistry.gauge("serialized.as.too.long.line", tags, 1.23);
        Gauge gauge = meterRegistry.find("serialized.as.too.long.line").gauge();
        assertThat(gauge).isNotNull();

        assertThat(exporter.toGaugeLine(gauge)).isEmpty();
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

        ArgumentCaptor<HttpSender.Request> argumentCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpClient).send(argumentCaptor.capture());
        HttpSender.Request request = argumentCaptor.getValue();

        assertThat(request.getRequestHeaders()).containsOnly(entry("Content-Type", "text/plain"),
                entry("User-Agent", "micrometer"), entry("Authorization", "Api-Token apiToken"));
        assertThat(request.getEntity()).asString()
            .hasLineCount(3)
            .contains("my.counter,dt.metrics.source=micrometer count,delta=12.0 " + clock.wallTime())
            .contains("my.gauge,dt.metrics.source=micrometer gauge,42.0 " + clock.wallTime())
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=22.0,max=22.0,sum=22.0,count=1 "
                    + clock.wallTime());
    }

    @Test
    void failOnSendShouldHaveProperLogging() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(500, "simulated"));

        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        exporter.export(Collections.singletonList(gauge));

        assertThat(LOGGER.getLogEvents())
            .contains(new LogEvent(ERROR, "Failed metric ingestion: Error Code=500, Response Body=simulated", null));
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
        String firstReqBody = new String(firstRequest.getEntity(), StandardCharsets.UTF_8);
        assertThat(firstReqBody).isEqualTo("test.counter,dt.metrics.source=micrometer count,delta=10.0");

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
        String secondReqBody = new String(secondRequest.getEntity(), StandardCharsets.UTF_8);
        assertThat(secondReqBody).isEqualTo("test.counter,dt.metrics.source=micrometer count,delta=30.0");
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

}
