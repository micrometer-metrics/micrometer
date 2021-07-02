/**
 * Copyright 2021 VMware, Inc.
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

package io.micrometer.dynatrace.v2;

import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.util.internal.logging.InternalMockLogger;
import io.micrometer.core.util.internal.logging.InternalMockLoggerFactory;
import io.micrometer.core.util.internal.logging.LogEvent;
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DynatraceExporterV2}.
 *
 * @author Georg Pirklbauer
 * @author Jonatan Ivanov
 */
class DynatraceExporterV2Test {
    private static final InternalMockLoggerFactory FACTORY = new InternalMockLoggerFactory();
    private static final InternalMockLogger LOGGER = FACTORY.getLogger(DynatraceExporterV2.class);

    private DynatraceConfig config;
    private MockClock clock;
    private HttpSender httpClient;
    private DynatraceMeterRegistry meterRegistry;
    private DynatraceExporterV2 exporter;

    @BeforeEach
    void setUp() {
        this.config = createDefaultDynatraceConfig();
        this.clock = new MockClock();
        this.clock.add(System.currentTimeMillis(), MILLISECONDS); // Set the clock to something recent so that the Dynatrace library will not complain.
        this.httpClient = mock(HttpSender.class);
        this.exporter = FACTORY.injectLogger(() -> createExporter(httpClient));

        this.meterRegistry = DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .httpClient(httpClient)
                .build();
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
        assertThat(lines.get(0)).isEqualTo("my.counter,dt.metrics.source=micrometer count,delta=3.0 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.functionCounter,dt.metrics.source=micrometer count,delta=2.3 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.timer,dt.metrics.source=micrometer gauge,min=0.0,max=60.0,sum=90.0,count=3 " + clock.wallTime());
    }

    @Test
    void toFunctionTimerLineShouldDropNanMean() {
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

            @Override
            @SuppressWarnings("NullableProblems")
            public double mean(TimeUnit unit) {
                return NaN;
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
        assertThat(lines.get(0)).isEqualTo("my.functionTimer,dt.metrics.source=micrometer gauge,min=10.0,max=10.0,sum=5000.0,count=500 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.longTaskTimer,dt.metrics.source=micrometer gauge,min=2000.0,max=48000.0,sum=236000.0,count=11 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.summary,dt.metrics.source=micrometer gauge,min=0.0,max=5.4,sum=10.9,count=4 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.gauge,tag1=value1,dt.metrics.source=micrometer,tag2=value2 gauge,1.23 " + clock.wallTime());
    }

    @Test
    void toGaugeLineShouldExportBlankTagValues() {
        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();

        List<String> lines = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.gauge,tag1=value1,dt.metrics.source=micrometer,tag2= gauge,1.23 " + clock.wallTime());
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
        assertThat(lines.get(0)).isEqualTo("my.counter,tag1=value1,dt.metrics.source=micrometer,tag2=value2 count,delta=0.0 " + clock.wallTime());
    }

    @Test
    void toCounterLineShouldExportBlankTagValues() {
        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        List<String> lines = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.counter,tag1=value1,dt.metrics.source=micrometer,tag2= count,delta=0.0 " + clock.wallTime());
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
        when(httpClient.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(202,
                "{ \"linesOk\": 3, \"linesInvalid\": 0, \"error\": null }"
        ));

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

        assertThat(request.getRequestHeaders()).containsOnly(
                entry("Content-Type", "text/plain"),
                entry("User-Agent", "micrometer"),
                entry("Authorization", "Api-Token apiToken")
        );
        assertThat(request.getEntity()).asString()
                .hasLineCount(3)
                .contains("my.counter,dt.metrics.source=micrometer count,delta=12.0 " + clock.wallTime())
                .contains("my.gauge,dt.metrics.source=micrometer gauge,42.0 " + clock.wallTime())
                .contains("my.timer,dt.metrics.source=micrometer gauge,min=22.0,max=22.0,sum=22.0,count=1 " + clock.wallTime());
    }

    @Test
    void failOnSendShouldHaveProperLogging() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(500, "simulated"));

        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        exporter.export(Collections.singletonList(gauge));

        assertThat(LOGGER.getLogEvents()).contains(new LogEvent(ERROR, "Failed metric ingestion: Error Code=500, Response Body=simulated", null));
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
