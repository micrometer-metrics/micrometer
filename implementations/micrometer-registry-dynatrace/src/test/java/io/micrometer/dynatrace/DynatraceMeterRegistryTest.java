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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Jonatan Ivanov
 */
class DynatraceMeterRegistryTest {

    private DynatraceConfig config;

    private MockClock clock;

    private HttpSender httpClient;

    private DynatraceMeterRegistry meterRegistry;

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
        this.meterRegistry = DynatraceMeterRegistry.builder(config).clock(clock).httpClient(httpClient).build();
    }

    @Test
    void shouldSendProperRequest() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class)))
            .thenReturn(new HttpSender.Response(202, "{ \"linesOk\": 3, \"linesInvalid\": 0, \"error\": null }"));

        Double gauge = meterRegistry.gauge("my.gauge", 42d);
        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(12d);
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(22, MILLISECONDS);
        timer.record(42, MILLISECONDS);
        timer.record(32, MILLISECONDS);
        timer.record(12, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();

        verify(httpClient).send(assertArg((request -> {
            assertThat(request.getRequestHeaders()).containsOnly(entry("Content-Type", "text/plain"),
                    entry("User-Agent", "micrometer"), entry("Authorization", "Api-Token apiToken"));

            String[] lines = new String(request.getEntity(), StandardCharsets.UTF_8).trim().split("\n");
            assertThat(lines).hasSize(4)
                .containsExactly("my.counter,dt.metrics.source=micrometer count,delta=12 " + clock.wallTime(),
                        "my.timer,dt.metrics.source=micrometer gauge,min=12,max=42,sum=108,count=4 " + clock.wallTime(),
                        "my.gauge,dt.metrics.source=micrometer gauge," + formatDouble(gauge) + " " + clock.wallTime(),
                        "#my.timer gauge dt.meta.unit=ms");
        })));
    }

    @Test
    void shouldResetBetweenRequests() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class)))
            .thenReturn(new HttpSender.Response(202, "{ \"linesOk\": 1, \"linesInvalid\": 0, \"error\": null }"));

        Timer timer = Timer.builder("my.timer").register(meterRegistry);
        timer.record(22, MILLISECONDS);
        timer.record(50, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();

        ArgumentCaptor<HttpSender.Request> argumentCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpClient).send(argumentCaptor.capture());
        HttpSender.Request request = argumentCaptor.getValue();

        assertThat(request.getEntity()).asString()
            .hasLineCount(2)
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=22,max=50,sum=72,count=2 " + clock.wallTime(),
                    "#my.timer gauge dt.meta.unit=ms");

        // both are bigger than the previous min and smaller than the previous max. They
        // will only show up if the
        // summary was reset in between exports.
        timer.record(33, MILLISECONDS);
        timer.record(44, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();
        ArgumentCaptor<HttpSender.Request> argumentCaptor2 = ArgumentCaptor.forClass(HttpSender.Request.class);
        // needs to be two, since the previous request is also counted.
        verify(httpClient, times(2)).send(argumentCaptor2.capture());
        HttpSender.Request request2 = argumentCaptor2.getValue();

        assertThat(request2.getEntity()).asString()
            .hasLineCount(2)
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=33,max=44,sum=77,count=2 " + clock.wallTime(),
                    "#my.timer gauge dt.meta.unit=ms");
    }

    @Test
    void shouldNotTrackPercentilesWithDynatraceSummary() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        Timer timer = Timer.builder("my.timer").publishPercentiles(0.5, 0.75, 0.9, 0.99).register(meterRegistry);
        timer.record(22, MILLISECONDS);
        timer.record(55, MILLISECONDS);

        clock.add(config.step());
        meterRegistry.publish();

        verify(httpClient).send(assertArg((request -> assertThat(request.getEntity()).asString()
            .hasLineCount(2)
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=22,max=55,sum=77,count=2 " + clock.wallTime(),
                    "#my.timer gauge dt.meta.unit=ms"))));
    }

    @Test
    void shouldTrackPercentilesWhenDynatraceSummaryInstrumentsNotUsed() throws Throwable {
        DynatraceConfig dynatraceConfig = getNonSummaryInstrumentsConfig();

        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(dynatraceConfig)
            .httpClient(httpClient)
            .clock(clock)
            .build();

        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);

        double[] trackedPercentiles = new double[] { 0.5, 0.7, 0.99 };

        Timer timer = Timer.builder("my.timer").publishPercentiles(trackedPercentiles).register(registry);
        DistributionSummary distributionSummary = DistributionSummary.builder("my.ds")
            .publishPercentiles(trackedPercentiles)
            .register(registry);
        CountDownLatch lttCountDownLatch = new CountDownLatch(1);
        LongTaskTimer longTaskTimer = LongTaskTimer.builder("my.ltt")
            .publishPercentiles(trackedPercentiles)
            .register(registry);

        timer.record(Duration.ofMillis(100));
        distributionSummary.record(100);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> longTaskTimer.record(() -> {
            clock.add(Duration.ofMillis(100));

            try {
                assertThat(lttCountDownLatch.await(300, MILLISECONDS)).isTrue();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        clock.add(dynatraceConfig.step());

        registry.publish();
        // release long task timer
        lttCountDownLatch.countDown();

        verify(httpClient).send(
                assertArg((request -> assertThat(request.getEntity()).asString()
                    .hasLineCount(16)
                    .contains(
                            // Timer lines
                            "my.timer,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 "
                                    + clock.wallTime(),
                            "#my.timer gauge dt.meta.unit=ms",
                            // Timer percentile lines. Percentiles are 0 because the step
                            // rolled over.
                            "my.timer.percentile,dt.metrics.source=micrometer,phi=0.5 gauge,0 " + clock.wallTime(),
                            "my.timer.percentile,dt.metrics.source=micrometer,phi=0.7 gauge,0 " + clock.wallTime(),
                            "my.timer.percentile,dt.metrics.source=micrometer,phi=0.99 gauge,0 " + clock.wallTime(),
                            "#my.timer.percentile gauge dt.meta.unit=ms",

                            // DistributionSummary lines
                            "my.ds,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 "
                                    + clock.wallTime(),
                            // DistributionSummary percentile lines. Percentiles are 0
                            // because the step rolled over.
                            "my.ds.percentile,dt.metrics.source=micrometer,phi=0.5 gauge,0 " + clock.wallTime(),
                            "my.ds.percentile,dt.metrics.source=micrometer,phi=0.7 gauge,0 " + clock.wallTime(),
                            "my.ds.percentile,dt.metrics.source=micrometer,phi=0.99 gauge,0 " + clock.wallTime(),

                            // LongTaskTimer lines
                            "my.ltt,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 "
                                    + clock.wallTime(),
                            "#my.ltt gauge dt.meta.unit=ms",
                            // LongTaskTimer percentile lines
                            // 0th percentile is missing because it doesn't clear the
                            // "interpolatable line" threshold defined in
                            // DefaultLongTaskTimer#takeSnapshot().
                            "my.ltt.percentile,dt.metrics.source=micrometer,phi=0.5 gauge,100 " + clock.wallTime(),
                            "my.ltt.percentile,dt.metrics.source=micrometer,phi=0.7 gauge,100 " + clock.wallTime(),
                            "my.ltt.percentile,dt.metrics.source=micrometer,phi=0.99 gauge,100 " + clock.wallTime(),
                            "#my.ltt.percentile gauge dt.meta.unit=ms"))));
    }

    @Test
    void shouldTrackPercentilesWhenDynatraceSummaryInstrumentsNotUsed_shouldExport0PercentileWhenSpecified()
            throws Throwable {
        DynatraceConfig dynatraceConfig = getNonSummaryInstrumentsConfig();

        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(dynatraceConfig)
            .httpClient(httpClient)
            .clock(clock)
            .build();

        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);

        // create instruments with an explicit 0 percentile. This should be exported.
        Timer timer = Timer.builder("my.timer").publishPercentiles(0, 0.5, 0.99).register(registry);
        DistributionSummary distributionSummary = DistributionSummary.builder("my.ds")
            .publishPercentiles(0, 0.5, 0.99)
            .register(registry);
        // For LongTaskTimer, the 0 percentile is not tracked as it doesn't clear the
        // "interpolatable line" threshold defined in DefaultLongTaskTimer#takeSnapshot().
        // see shouldTrackPercentilesWhenDynatraceSummaryInstrumentsNotUsed for a test
        // that exports LongTaskTimer percentiles

        timer.record(Duration.ofMillis(100));
        distributionSummary.record(100);

        clock.add(dynatraceConfig.step());

        registry.publish();

        verify(httpClient)
            .send(assertArg((request -> assertThat(request.getEntity()).asString()
                .hasLineCount(10)
                .contains(
                        // Timer lines
                        "my.timer,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 "
                                + clock.wallTime(),
                        "#my.timer gauge dt.meta.unit=ms",
                        // Timer percentile lines. Percentiles are 0 because the step
                        // rolled over.
                        "my.timer.percentile,dt.metrics.source=micrometer,phi=0 gauge,0 " + clock.wallTime(),
                        "my.timer.percentile,dt.metrics.source=micrometer,phi=0.5 gauge,0 " + clock.wallTime(),
                        "my.timer.percentile,dt.metrics.source=micrometer,phi=0.99 gauge,0 " + clock.wallTime(),
                        "#my.timer.percentile gauge dt.meta.unit=ms",

                        // DistributionSummary lines
                        "my.ds,dt.metrics.source=micrometer gauge,min=100,max=100,sum=100,count=1 " + clock.wallTime(),
                        // DistributionSummary percentile lines. Percentiles are 0 because
                        // the step rolled over.
                        "my.ds.percentile,dt.metrics.source=micrometer,phi=0 gauge,0 " + clock.wallTime(),
                        "my.ds.percentile,dt.metrics.source=micrometer,phi=0.5 gauge,0 " + clock.wallTime(),
                        "my.ds.percentile,dt.metrics.source=micrometer,phi=0.99 gauge,0 " + clock.wallTime()))));
    }

    @Test
    void shouldNotExportLinesWithZeroCount() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        Timer timer = Timer.builder("my.timer").register(meterRegistry);

        // ---> first export interval, one request is sent:
        timer.record(44, MILLISECONDS);
        clock.add(config.step());
        meterRegistry.publish();

        verify(httpClient).send(assertArg(request -> assertThat(request.getEntity()).asString()
            .hasLineCount(2)
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=44,max=44,sum=44,count=1 " + clock.wallTime(),
                    "#my.timer gauge dt.meta.unit=ms")));

        // reset for next export interval
        reset(httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);

        // ---> second export interval, no values are recorded
        clock.add(config.step());
        meterRegistry.publish();

        // if the line has 0 count, don't send anything
        verify(httpClient, never()).send(any());

        // reset for next export interval
        reset(httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);

        // ---> third export interval
        timer.record(33, MILLISECONDS);
        clock.add(config.step());
        meterRegistry.publish();

        verify(httpClient).send(assertArg(request -> assertThat(request.getEntity()).asString()
            .hasLineCount(2)
            .contains("my.timer,dt.metrics.source=micrometer gauge,min=33,max=33,sum=33,count=1 " + clock.wallTime(),
                    "#my.timer gauge dt.meta.unit=ms")));
    }

    private DynatraceConfig createDefaultDynatraceConfig() {
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

    private static DynatraceConfig getNonSummaryInstrumentsConfig() {
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

            @Override
            public boolean useDynatraceSummaryInstruments() {
                return false;
            }
        };
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }

        return Double.toString(value);
    }

}
