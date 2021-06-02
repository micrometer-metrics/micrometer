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
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.Test;
import wiremock.com.google.common.util.concurrent.AtomicDouble;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DynatraceExporterV2Test {
    private final MockClock clock = createMockClock();
    private final DynatraceConfig config = createDynatraceConfig();
    private final DynatraceMeterRegistry meterRegistry = createMeterRegistry();
    private final DynatraceExporterV2 exporter = createExporter();

    private MockClock createMockClock() {
        MockClock clock = new MockClock();
        // Set the clock to something recent so that the Dynatrace library will not complain.
        clock.add(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        return clock;
    }

    private DynatraceConfig createDynatraceConfig() {
        return new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "http://localhost";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }
        };
    }

    private DynatraceMeterRegistry createMeterRegistry() {
        return DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .httpClient(request -> new HttpSender.Response(200, null))
                .build();
    }

    private DynatraceExporterV2 createExporter() {
        return new DynatraceExporterV2(config, clock, request -> new HttpSender.Response(200, null));
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
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toGaugeLineShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge).collect(Collectors.toList())).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(exporter.toGaugeLine(gauge).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toTimeGaugeLine() {
        AtomicReference<Double> obj = new AtomicReference<>(2.3d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        List<String> lines = exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.timeGauge,dt.metrics.source=micrometer gauge,2.3 " + clock.wallTime());
    }

    @Test
    void toTimeGaugeLineShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();

        assertThat(exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toTimeGaugeLineShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.MILLISECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList())).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.MILLISECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList())).isEmpty();
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
        counter.increment(Double.NaN);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toCounterLineShouldDropInfiniteValue() {
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(Double.POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toCounterLine(counter).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toFunctionCounterLine() {
        AtomicDouble obj = new AtomicDouble();
        FunctionCounter.builder("my.functionCounter", obj, Number::doubleValue).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertNotNull(functionCounter);

        obj.addAndGet(2.3d);
        clock.add(config.step());

        List<String> lines = exporter.toFunctionCounterLine(functionCounter).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.functionCounter,dt.metrics.source=micrometer count,delta=2.3 " + clock.wallTime());
    }

    @Test
    void toFunctionCounterLineShouldDropNanValue() {
        AtomicDouble obj = new AtomicDouble();
        FunctionCounter.builder("my.functionCounter", obj, Number::doubleValue).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertNotNull(functionCounter);

        obj.addAndGet(Double.NaN);
        clock.add(config.step());

        assertThat(exporter.toFunctionCounterLine(functionCounter).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toFunctionCounterLineShouldDropInfiniteValue() {
        AtomicDouble obj = new AtomicDouble();
        FunctionCounter.builder("my.functionCounter", obj, Number::doubleValue).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.functionCounter").functionCounter();
        assertNotNull(functionCounter);

        obj.addAndGet(Double.POSITIVE_INFINITY);
        clock.add(config.step());

        assertThat(exporter.toFunctionCounterLine(functionCounter).collect(Collectors.toList())).isEmpty();
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
            public double totalTime(TimeUnit unit) {
                return 5000;
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return TimeUnit.MILLISECONDS;
            }

            @Override
            public Id getId() {
                return new Id("my.functionTimer", Tags.empty(), null, null, Type.TIMER);
            }

            @Override
            public double mean(TimeUnit unit) {
                return Double.NaN;
            }
        };

        assertThat(exporter.toFunctionTimerLine(functionTimer).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void toFunctionTimerLine() {
        FunctionTimer functionTimer = new FunctionTimer() {
            @Override
            public double count() {
                return 500;
            }

            @Override
            public double totalTime(TimeUnit unit) {
                return 5000;
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return TimeUnit.MILLISECONDS;
            }

            @Override
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
        List<Integer> samples = Arrays.asList(42, 48, 40, 35, 22, 16, 13, 8, 6, 2, 4);
        int prior = samples.get(0);
        for (Integer value : samples) {
            clock.add(prior - value, TimeUnit.SECONDS);
            longTaskTimer.start();
            prior = value;
        }
        clock(meterRegistry).add(samples.get(samples.size() - 1), TimeUnit.SECONDS);

        List<String> lines = exporter.toLongTaskTimerLine(longTaskTimer).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.longTaskTimer,dt.metrics.source=micrometer gauge,min=4000.0,max=42000.0,sum=236000.0,count=11 " + clock.wallTime());
    }

    @Test
    void testToDistributionSummaryLine() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        summary.record(2.3);
        summary.record(5.4);
        summary.record(.1);
        clock.add(config.step());

        List<String> lines = exporter.toDistributionSummaryLine(summary).collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("my.summary,dt.metrics.source=micrometer gauge,min=0.0,max=5.4,sum=10.9,count=4 " + clock.wallTime());
    }

    @Test
    void toMeterLine() {
        meterRegistry.gauge("my.meter", 1.23);
        Gauge gauge = meterRegistry.find("my.meter").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toMeterLine(gauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.meter,dt.metrics.source=micrometer gauge,1.23");
        String expectedDummy = "my.meter,dt.metrics.source=micrometer gauge,1.23 1617714022879";
        assertThat(actual.get(0)).hasSize(expectedDummy.length());
    }

    @Test
    void toGaugeInvalidName() {
        // invalid name.
        meterRegistry.gauge("~~~", 1.23);
        Gauge gauge = meterRegistry.find("~~~").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(actual).isEmpty();
    }

    @Test
    void toGaugeInvalidCases() {
        meterRegistry.gauge("my.gauge1", Double.NaN);
        Gauge gauge1 = meterRegistry.find("my.gauge1").gauge();
        assertNotNull(gauge1);
        List<String> actual1 = exporter.toGaugeLine(gauge1).collect(Collectors.toList());
        assertThat(actual1).isEmpty();

        meterRegistry.gauge("my.gauge2", Double.NEGATIVE_INFINITY);
        Gauge gauge2 = meterRegistry.find("my.gauge2").gauge();
        assertNotNull(gauge2);
        List<String> actual2 = exporter.toGaugeLine(gauge2).collect(Collectors.toList());
        assertThat(actual2).isEmpty();

        meterRegistry.gauge("my.gauge3", Double.POSITIVE_INFINITY);
        Gauge gauge3 = meterRegistry.find("my.gauge3").gauge();
        assertNotNull(gauge3);
        List<String> actual3 = exporter.toGaugeLine(gauge3).collect(Collectors.toList());
        assertThat(actual3).isEmpty();
    }

    @Test
    void toGaugeTags() {
        // invalid name.
        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).contains("tag1=value1").contains("tag2=value2").contains("dt.metrics.source=micrometer");
        assertThat(actual.get(0)).startsWith("my.gauge,");
        assertThat(actual.get(0)).hasSize("my.gauge,dt.metrics.source=micrometer,tag1=value1,tag2=value2 gauge,1.23 1617776498381".length());
    }

    @Test
    void toCounterInvalidName() {
        // invalid name.
        meterRegistry.counter("~~~");
        Counter counter = meterRegistry.find("~~~").counter();
        assertNotNull(counter);

        List<String> actual = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(actual).isEmpty();
    }

    @Test
    void toCounterInvalidCases() {
        meterRegistry.counter("my.counter1");
        Counter counter = meterRegistry.find("my.counter1").counter();
        assertNotNull(counter);

        // NaN and infinity are ignored. The counter value stays at 0.0 when adding one of NaN,
        // +Inf or -Inf.
        counter.increment(Double.NaN);
        List<String> actual1 = exporter.toCounterLine(counter).collect(Collectors.toList());
        counter.increment(Double.POSITIVE_INFINITY);
        List<String> actual2 = exporter.toCounterLine(counter).collect(Collectors.toList());
        counter.increment(Double.NEGATIVE_INFINITY);
        List<String> actual3 = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(actual1).hasSize(1);
        assertThat(actual2).hasSize(1);
        assertThat(actual3).hasSize(1);

        assertThat(actual1.get(0)).contains("count,delta=0");
        assertThat(actual2.get(0)).contains("count,delta=0");
        assertThat(actual3.get(0)).contains("count,delta=0");
    }

    @Test
    void toCounterTags() {
        // invalid name.
        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertNotNull(counter);

        List<String> actual = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).contains("tag1=value1").contains("tag2=value2").contains("dt.metrics.source=micrometer");
        assertThat(actual.get(0)).startsWith("my.counter,");
        assertThat(actual.get(0)).hasSize("my.counter,tag1=value1,dt.metrics.source=micrometer,tag2=value2 count,delta=0.0 1617796526714".length());
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

        List<String> actual = exporter.toGaugeLine(gauge).collect(Collectors.toList());
        assertThat(actual).isEmpty();
    }
}
