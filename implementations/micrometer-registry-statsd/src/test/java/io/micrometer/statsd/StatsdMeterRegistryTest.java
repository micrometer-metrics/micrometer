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
package io.micrometer.statsd;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.options.ClientOptions;
import reactor.ipc.netty.udp.UdpClient;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jon Schneider
 */
class StatsdMeterRegistryTest {
    /**
     * A port that is NOT the default for DogStatsD or Telegraf, so these unit tests
     * do not fail if one of those agents happens to be running on the same box.
     */
    private static final int PORT = 8126;
    private MockClock mockClock = new MockClock();

    @BeforeAll
    static void before() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    private void assertLines(Consumer<StatsdMeterRegistry> registryAction, StatsdFlavor flavor, String... expected) {
        final CountDownLatch bindLatch = new CountDownLatch(1);
        final CountDownLatch receiveLatch = new CountDownLatch(1);

        final AtomicReference<String> result = new AtomicReference<>();
        final Disposable.Swap server = Disposables.swap();

        final StatsdMeterRegistry registry = registry(flavor);

        Consumer<ClientOptions.Builder<?>> opts = builder -> builder.option(ChannelOption.SO_REUSEADDR, true)
            .connectAddress(() -> new InetSocketAddress(PORT));

        UdpClient.create(opts)
            .newHandler((in, out) -> {
                in.receive()
                    .asString()
                    .subscribe(line -> {
                        // ignore gauges monitoring the registry itself
                        if(line.startsWith("statsd"))
                            return;

                        result.set(line);
                        receiveLatch.countDown();
                    });
                return Flux.never();
            })
            .doOnSuccess(v -> bindLatch.countDown())
            .subscribe(server::replace);

        try {
            assertTrue(bindLatch.await(1, TimeUnit.SECONDS));

            try {
                registryAction.accept(registry);
            } catch (Throwable t) {
                fail("Failed to perform registry action", t);
            }

            assertTrue(receiveLatch.await(10, TimeUnit.SECONDS));
            assertThat(result.get().split("\n")).contains(expected);
        } catch (InterruptedException e) {
            fail("Failed to wait for line", e);
        } finally {
            server.dispose();
            registry.stop();
        }
    }

    private StatsdMeterRegistry registry(StatsdFlavor flavor) {
        return new StatsdMeterRegistry(new StatsdConfig() {
            @Override
            public String get(String k) {
                return null;
            }

            @Override
            public int port() {
                return PORT;
            }

            @Override
            public StatsdFlavor flavor() {
                return flavor;
            }

            @Override
            public Duration pollingFrequency() {
                return Duration.ofMillis(1);
            }
        }, mockClock);
    }

    @ParameterizedTest
    @EnumSource(StatsdFlavor.class)
    void counterLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case Etsy:
                line = "myCounter.myTag.val.statistic.count:2|c";
                break;
            case Datadog:
                line = "my.counter:2|c|#statistic:count,my.tag:val";
                break;
            case Telegraf:
                line = "myCounter,statistic=count,myTag=val:2|c";
        }

        assertLines(r -> r.counter("my.counter", "my.tag", "val").increment(2.1), flavor, line);
    }

    @ParameterizedTest
    @EnumSource(StatsdFlavor.class)
    void gaugeLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case Etsy:
                line = "myGauge.myTag.val.statistic.value:2|g";
                break;
            case Datadog:
                line = "my.gauge:2|g|#statistic:value,my.tag:val";
                break;
            case Telegraf:
                line = "myGauge,statistic=value,myTag=val:2|g";
                break;
        }

        Integer n = 2;
        assertLines(r -> r.gauge("my.gauge", Tags.zip("my.tag", "val"), n), flavor, line);
    }

    @ParameterizedTest
    @EnumSource(StatsdFlavor.class)
    void timerLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case Etsy:
                line = "myTimer.myTag.val:1|ms";
                break;
            case Datadog:
                line = "my.timer:1|ms|#my.tag:val";
                break;
            case Telegraf:
                line = "myTimer,myTag=val:1|ms";
        }

        assertLines(r -> r.timer("my.timer", "my.tag", "val").record(1, TimeUnit.MILLISECONDS),
            flavor, line);
    }

    @ParameterizedTest
    @EnumSource(StatsdFlavor.class)
    void summaryLineProtocol(StatsdFlavor flavor) {
        String line = null;
        switch (flavor) {
            case Etsy:
                line = "mySummary.myTag.val:1|h";
                break;
            case Datadog:
                line = "my.summary:1|h|#my.tag:val";
                break;
            case Telegraf:
                line = "mySummary,myTag=val:1|h";
        }

        assertLines(r -> r.summary("my.summary", "my.tag", "val").record(1), flavor, line);
    }

    @ParameterizedTest
    @EnumSource(StatsdFlavor.class)
    void longTaskTimerLineProtocol(StatsdFlavor flavor) {
        final Function<MeterRegistry, LongTaskTimer> ltt = r -> r.more().longTaskTimer("my.long.task", "my.tag", "val");

        StepVerifier
            .withVirtualTime(() -> {
                String[] lines = null;
                switch (flavor) {
                    case Etsy:
                        lines = new String[]{
                            "myLongTask.myTag.val.statistic.activetasks:1|c",
                            "myLongTaskDuration.myTag.val.statistic.value:1|c",
                        };
                        break;
                    case Datadog:
                        lines = new String[]{
                            "my.long.task:1|c|#statistic:activetasks,myTag:val",
                            "my.long.task:1|c|#statistic:duration,myTag:val",
                        };
                        break;
                    case Telegraf:
                        lines = new String[]{
                            "myLongTask,statistic=activetasks,myTag=val:1|c",
                            "myLongTask,statistic=duration,myTag=val:1|c",
                        };
                }

                assertLines(r -> ltt.apply(r).start(), flavor, lines);
                return null;
            })
            .then(() -> mockClock.add(10, TimeUnit.MILLISECONDS))
            .thenAwait(Duration.ofMillis(10));
    }
}