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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

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
                        "#my.timer gauge dt.meta.unit=milliseconds");
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
                    "#my.timer gauge dt.meta.unit=milliseconds");

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
                    "#my.timer gauge dt.meta.unit=milliseconds");
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
                    "#my.timer gauge dt.meta.unit=milliseconds"))));
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
                    "#my.timer gauge dt.meta.unit=milliseconds")));

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
                    "#my.timer gauge dt.meta.unit=milliseconds")));
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

    private String formatDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }

        return Double.toString(value);
    }

}
