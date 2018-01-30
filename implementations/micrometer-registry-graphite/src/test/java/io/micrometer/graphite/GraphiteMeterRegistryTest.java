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
package io.micrometer.graphite;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.lang.Nullable;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.options.ClientOptions;
import reactor.ipc.netty.udp.UdpServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphiteMeterRegistryTest {
    /**
     * A port that is NOT the default for DogStatsD or Telegraf, so these unit tests
     * do not fail if one of those agents happens to be running on the same box.
     */
    private static final int PORT = 8127;
    private MockClock mockClock = new MockClock();

    @Test
    void metricPrefixes() {
        final CountDownLatch bindLatch = new CountDownLatch(1);
        final CountDownLatch receiveLatch = new CountDownLatch(1);
        final CountDownLatch terminateLatch = new CountDownLatch(1);

        final GraphiteMeterRegistry registry = new GraphiteMeterRegistry(new GraphiteConfig() {
            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }

            @Override
            public GraphiteProtocol protocol() {
                return GraphiteProtocol.UDP;
            }

            @Override
            public int port() {
                return 8127;
            }

            @Override
            public String[] tagsAsPrefix() {
                return new String[]{"application"};
            }
        }, mockClock);

        final Disposable.Swap server = Disposables.swap();

        Consumer<ClientOptions.Builder<?>> opts = builder -> builder.option(ChannelOption.SO_REUSEADDR, true)
            .connectAddress(() -> new InetSocketAddress(PORT));

        UdpServer.create(opts)
            .newHandler((in, out) -> {
                in.receive()
                    .asString()
                    .log()
                    .subscribe(line -> {
                        assertThat(line).startsWith("APPNAME.myTimer");
                        receiveLatch.countDown();
                    });
                return Flux.never();
            })
            .doOnSuccess(v -> bindLatch.countDown())
            .doOnTerminate(terminateLatch::countDown)
            .subscribe(server::replace);

        try {
            assertTrue(bindLatch.await(10, TimeUnit.SECONDS));
            registry.timer("my.timer", "application", "APPNAME");
            assertTrue(receiveLatch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Failed to wait for line", e);
        } finally {
            server.dispose();
            registry.stop();
            try {
                terminateLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Failed to terminate UDP server listening for Graphite metrics", e);
            }
        }
    }
}