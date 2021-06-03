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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Jonatan Ivanov
 */
public class DynatraceMeterRegistryTest {
    private DynatraceConfig config;
    private MockClock clock;
    private HttpSender httpClient;
    private DynatraceMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        this.config = createDefaultDynatraceConfig();
        this.clock = new MockClock();
        this.clock.add(System.currentTimeMillis(), MILLISECONDS); // Set the clock to something recent so that the Dynatrace library will not complain.
        this.httpClient = mock(HttpSender.class);
        this.meterRegistry = DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .httpClient(httpClient)
                .build();
    }

    @Test
    void shouldSendProperRequest() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        when(httpClient.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(202,
                "{ \"linesOk\": 4, \"linesInvalid\": 0, \"error\": null }"
        ));

        Counter counter = meterRegistry.counter("my.counter");
        counter.increment(12d);
        meterRegistry.gauge("my.gauge", 42d);
        Timer timer = meterRegistry.timer("my.timer");
        timer.record(22, MILLISECONDS);
        timer.record(42, MILLISECONDS);
        timer.record(32, MILLISECONDS);
        timer.record(12, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();

        ArgumentCaptor<HttpSender.Request> argumentCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpClient).send(argumentCaptor.capture());
        HttpSender.Request request = argumentCaptor.getValue();

        assertThat(request.getRequestHeaders()).containsOnly(
                entry("Content-Type", "text/plain"),
                entry("User-Agent", "micrometer"),
                entry("Authorization", "Api-Token apiToken")
        );
        assertThat(request.getEntity()).asString()
                .hasLineCount(4)
                .contains("my.counter,dt.metrics.source=micrometer count,delta=12.0 " + clock.wallTime())
                .contains("my.gauge,dt.metrics.source=micrometer gauge,42.0 " + clock.wallTime())
                .contains("my.timer,dt.metrics.source=micrometer gauge,min=0.0,max=42.0,sum=108.0,count=4 " + clock.wallTime())
                .contains("my.timer.percentile,phi=0,dt.metrics.source=micrometer gauge,0.0 " + clock.wallTime());
    }

    @Test
    void shouldAddZerothPercentileToExistingOnes() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        Timer timer = Timer.builder("my.timer")
                .publishPercentiles(0.5)
                .register(meterRegistry);
        timer.record(22, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();

        ArgumentCaptor<HttpSender.Request> argumentCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpClient).send(argumentCaptor.capture());
        HttpSender.Request request = argumentCaptor.getValue();

        assertThat(request.getEntity()).asString()
                .hasLineCount(3)
                .contains("my.timer,dt.metrics.source=micrometer gauge,min=22.0,max=22.0,sum=22.0,count=1 " + clock.wallTime())
                .contains("my.timer.percentile,phi=0,dt.metrics.source=micrometer gauge,0.0 " + clock.wallTime())
                .contains("my.timer.percentile,phi=0.5,dt.metrics.source=micrometer gauge,0.0 " + clock.wallTime());
    }

    @Test
    void shouldNotAddZerothPercentileIfAlreadyDefined() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(config.uri(), httpClient);
        when(httpClient.post(config.uri())).thenReturn(builder);
        Timer timer = Timer.builder("my.timer")
                .publishPercentiles(0.5, 0.0)
                .register(meterRegistry);
        timer.record(22, MILLISECONDS);
        clock.add(config.step());

        meterRegistry.publish();

        ArgumentCaptor<HttpSender.Request> argumentCaptor = ArgumentCaptor.forClass(HttpSender.Request.class);
        verify(httpClient).send(argumentCaptor.capture());
        HttpSender.Request request = argumentCaptor.getValue();

        assertThat(request.getEntity()).asString()
                .hasLineCount(3)
                .contains("my.timer,dt.metrics.source=micrometer gauge,min=22.0,max=22.0,sum=22.0,count=1 " + clock.wallTime())
                .contains("my.timer.percentile,phi=0,dt.metrics.source=micrometer gauge,0.0 " + clock.wallTime())
                .contains("my.timer.percentile,phi=0.5,dt.metrics.source=micrometer gauge,0.0 " + clock.wallTime());
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
