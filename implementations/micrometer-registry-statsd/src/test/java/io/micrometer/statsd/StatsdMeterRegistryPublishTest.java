/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link StatsdMeterRegistry} metrics publishing functionality.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
class StatsdMeterRegistryPublishTest {
    private static final String UDS_DATAGRAM_SOCKET_PATH = "/tmp/test-server.sock";

    StatsdMeterRegistry meterRegistry;
    DisposableChannel server;
    CountDownLatch serverLatch;
    AtomicInteger serverMetricReadCount = new AtomicInteger();

    volatile boolean bound;

    @AfterEach
    void cleanUp() {
        if (meterRegistry != null) {
            meterRegistry.close();
        }
        if (server != null) {
            server.disposeNow();
        }
    }

    @Issue("2880")
    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void receiveParallelMetricsSuccessfully(StatsdProtocol protocol) throws InterruptedException {
        final int N = 10;

        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(N);
        server = startServer(protocol, 0);
        final int port = getPort(protocol);

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);

        IntStream.range(0, N)
                .parallel()
                .forEach(ignored -> counter.increment());

        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void receiveMetricsSuccessfully(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(3);
        server = startServer(protocol, 0);

        final int port = getPort(protocol);

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment();
        counter.increment();
        counter.increment();
        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void resumeSendingMetrics_whenServerIntermittentlyFails(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(1);
        AtomicInteger writeCount = new AtomicInteger();
        server = startServer(protocol, 0);

        final int port = getPort(protocol);

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        trackWritesForUdpClient(protocol, writeCount);
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment(1);
        assertThat(serverLatch.await(5, TimeUnit.SECONDS)).isTrue();

        Disposable firstClient = meterRegistry.statsdConnection.get();

        server.disposeNow();
        serverLatch = new CountDownLatch(3);
        // client will try to send but server is down
        IntStream.range(2, 5).forEach(counter::increment);
        if (protocol == StatsdProtocol.UDP) {
            await().until(() -> writeCount.get() == 4);
        }
        server = startServer(protocol, port);
        assertThat(serverLatch.getCount()).isEqualTo(3);

        await().until(() -> bound);

        // Note that this guarantees this test to be passed.
        // For TCP, this will help trigger replacing client. If this triggered replacing, this change will be lost.
        // For UDP, the first change seems to be lost frequently somehow.
        Counter.builder("another.counter").register(meterRegistry).increment();

        if (protocol == StatsdProtocol.TCP || protocol == StatsdProtocol.UDS_DATAGRAM) {
            await().until(() -> meterRegistry.statsdConnection.get() != firstClient);
        }

        counter.increment(5);
        counter.increment(6);
        counter.increment(7);
        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    @Issue("#1676")
    void stopAndStartMeterRegistrySendsMetrics(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(3);
        server = startServer(protocol, 0);

        final int port = getPort(protocol);

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment();
        await().until(() -> serverLatch.getCount() == 2);
        meterRegistry.stop();
        await().until(this::clientIsDisposed);
        // These increments shouldn't be sent
        IntStream.range(0, 3).forEach(i -> counter.increment());
        startRegistryAndWaitForClient();
        assertThat(serverLatch.getCount()).isEqualTo(2);
        counter.increment();
        counter.increment();
        assertThat(serverLatch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Issue("#1676")
    void stopAndStartMeterRegistryWithLineSink() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        meterRegistry = StatsdMeterRegistry.builder(StatsdConfig.DEFAULT).lineSink(s -> latch.countDown()).build();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment();
        meterRegistry.stop();
        // These increments shouldn't be processed
        IntStream.range(0, 3).forEach(i -> counter.increment());
        meterRegistry.start();
        assertThat(latch.getCount()).isEqualTo(2);
        counter.increment();
        counter.increment();
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void whenBackendInitiallyDown_metricsSentAfterBackendStarts(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        AtomicInteger writeCount = new AtomicInteger();
        serverLatch = new CountDownLatch(3);
        // start server to secure an open port
        server = startServer(protocol, 0);
        final int port = getPort(protocol);
        server.disposeNow();
        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        meterRegistry.start();
        trackWritesForUdpClient(protocol, writeCount);
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        IntStream.range(1, 4).forEach(counter::increment);
        if (protocol == StatsdProtocol.UDP) {
            await().until(() -> writeCount.get() == 3);
        }
        server = startServer(protocol, port);
        if (protocol == StatsdProtocol.TCP || protocol == StatsdProtocol.UDS_DATAGRAM) {
            // client is null until connection established
            await().until(() -> meterRegistry.statsdConnection.get() != null);
            // client may take some time to reconnect to the server
            await().until(() -> !clientIsDisposed());
        }
        assertThat(serverLatch.getCount()).isEqualTo(3);

        if (protocol == StatsdProtocol.UDP) {
            // Note that this guarantees this test to be passed.
            // For UDP, the first change seems to be lost frequently somehow.
            Counter.builder("another.counter").register(meterRegistry).increment();
        }

        counter.increment();
        counter.increment();
        counter.increment();
        assertThat(serverLatch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void whenRegistryStopped_doNotConnectToBackend(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(3);
        // start server to secure an open port
        server = startServer(protocol, 0);
        final int port = getPort(protocol);
        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        server.disposeNow();
        meterRegistry.stop();
        await().until(this::clientIsDisposed);
        server = startServer(protocol, port);
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        IntStream.range(0, 100).forEach(counter::increment);
        assertThat(serverLatch.await(1, TimeUnit.SECONDS)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    @Issue("#2177")
    void whenSendError_reconnectsAndWritesNewMetrics(StatsdProtocol protocol) throws InterruptedException {
        skipUdsTestOnWindows(protocol);
        serverLatch = new CountDownLatch(3);
        server = startServer(protocol, 0);
        final int port = getPort(protocol);
        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        ((Connection) meterRegistry.statsdConnection.get()).addHandler("writeFailure", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                throw new RuntimeException("write error for testing purposes");
            }
        });
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        // write will cause error
        counter.increment();
        // wait for reconnect
        await().until(() -> !clientIsDisposed());
        // remove write exception handler
        ((Connection) meterRegistry.statsdConnection.get()).removeHandler("writeFailure");
        IntStream.range(1, 4).forEach(counter::increment);
        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> serverMetricReadCount.get() == 3);
    }

    private void skipUdsTestOnWindows(StatsdProtocol protocol) {
        if (protocol == StatsdProtocol.UDS_DATAGRAM)
            assumeTrue(!OS.WINDOWS.isCurrentOs());
    }

    private int getPort(StatsdProtocol protocol) {
        if (protocol == StatsdProtocol.UDS_DATAGRAM) return 0;
        return ((InetSocketAddress) server.address()).getPort();
    }

    private void trackWritesForUdpClient(StatsdProtocol protocol, AtomicInteger writeCount) {
        if (protocol == StatsdProtocol.UDP) {
            await().until(() -> meterRegistry.statsdConnection.get() != null);
            ((Connection) meterRegistry.statsdConnection.get())
                    .addHandler(new LoggingHandler("testudpclient", LogLevel.INFO))
                    .addHandler(new ChannelOutboundHandlerAdapter() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            writeCount.incrementAndGet();
                            super.write(ctx, msg, promise);
                        }
                    });
        }
    }

    private void startRegistryAndWaitForClient() {
        meterRegistry.start();
        await().until(() -> !clientIsDisposed());
    }

    private boolean clientIsDisposed() {
        return meterRegistry.statsdConnection.get().isDisposed();
    }

    private DisposableChannel startServer(StatsdProtocol protocol, int port) {
        if (protocol == StatsdProtocol.UDP || protocol == StatsdProtocol.UDS_DATAGRAM) {
            return UdpServer.create()
                    .bindAddress(() -> protocol == StatsdProtocol.UDP ? InetSocketAddress.createUnresolved("localhost", port)
                            : newDomainSocketAddress())
                    .handle((in, out) ->
                            in.receive().asString()
                                    .flatMap(packet -> {
                                        serverLatch.countDown();
                                        serverMetricReadCount.getAndIncrement();
                                        return Flux.never();
                                    }))
                    .doOnBound((server) -> bound = true)
                    .doOnUnbound((server) -> bound = false)
                    .wiretap("udpserver", LogLevel.INFO)
                    .bindNow(Duration.ofSeconds(2));
        } else if (protocol == StatsdProtocol.TCP) {
            AtomicReference<DisposableChannel> channel = new AtomicReference<>();
            return TcpServer.create()
                    .host("localhost")
                    .port(port)
                    .handle((in, out) ->
                            in.receive().asString()
                                    .flatMap(packet -> {
                                        IntStream.range(0, packet.split("my.counter").length - 1).forEach(i -> {
                                            serverLatch.countDown();
                                            serverMetricReadCount.getAndIncrement();
                                        });
                                        in.withConnection(channel::set);
                                        return Flux.never();
                                    }))
                    .doOnBound((server) -> bound = true)
                    .doOnUnbound((server) -> {
                        bound = false;
                        if (channel.get() != null) {
                            channel.get().dispose();
                        }
                    })
                    .wiretap("tcpserver", LogLevel.INFO)
                    .bindNow(Duration.ofSeconds(5));
        } else {
            throw new IllegalArgumentException("test implementation does not currently support the protocol " + protocol);
        }
    }

    private static DomainSocketAddress newDomainSocketAddress() {
        try {
            File tempFile = new File(UDS_DATAGRAM_SOCKET_PATH);
            tempFile.delete();
            tempFile.deleteOnExit();
            return new DomainSocketAddress(tempFile);
        }
        catch (Exception e) {
            throw new RuntimeException("Error creating a temporary file", e);
        }
    }

    private StatsdConfig getUnbufferedConfig(StatsdProtocol protocol, int port) {
        return new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String host() {
                return protocol == StatsdProtocol.UDS_DATAGRAM ? UDS_DATAGRAM_SOCKET_PATH : "localhost";
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public StatsdProtocol protocol() {
                return protocol;
            }

            @Override
            public boolean buffered() {
                return false;
            }
        };
    }
}
