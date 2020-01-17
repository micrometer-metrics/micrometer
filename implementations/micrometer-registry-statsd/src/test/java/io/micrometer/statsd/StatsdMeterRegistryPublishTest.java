/**
 * Copyright 2017 Pivotal Software, Inc.
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
import io.netty.handler.logging.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableChannel;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpServer;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests {@link StatsdMeterRegistry} metrics publishing functionality.
 */
class StatsdMeterRegistryPublishTest {

    StatsdMeterRegistry meterRegistry;
    CountDownLatch serverLatch;

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void receiveMetricsSuccessfully(StatsdProtocol protocol) throws InterruptedException {
        serverLatch = new CountDownLatch(3);
        DisposableChannel server = startServer(protocol, 0);

        final int port = server.address().getPort();

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment();
        counter.increment();
        counter.increment();
        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
        meterRegistry.close();

        server.disposeNow();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void resumeSendingMetrics_whenServerIntermittentlyFails(StatsdProtocol protocol) throws InterruptedException {
        serverLatch = new CountDownLatch(1);
        DisposableChannel server = startServer(protocol, 0);

        final int port = server.address().getPort();

        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        startRegistryAndWaitForClient();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        counter.increment(1);
        assertThat(serverLatch.await(5, TimeUnit.SECONDS)).isTrue();
        // stop server
        server.disposeNow();
        serverLatch = new CountDownLatch(3);
        // client will try to send but server is down
        IntStream.range(2, 100).forEach(counter::increment);
        // TODO wait until processor queue is drained...
        // start server
        server = startServer(protocol, port);
        assertThat(serverLatch.getCount()).isEqualTo(3);
        // make sure the client is connected before making counter increments
        await().until(() -> !clientIsDisposed());
        counter.increment(5);
        counter.increment(6);
        counter.increment(7);
        assertThat(serverLatch.await(3, TimeUnit.SECONDS)).isTrue();
        meterRegistry.close();

        server.disposeNow();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    @Issue("#1676")
    void stopAndStartMeterRegistrySendsMetrics(StatsdProtocol protocol) throws InterruptedException {
        serverLatch = new CountDownLatch(3);
        DisposableChannel server = startServer(protocol, 0);

        final int port = server.address().getPort();

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
        meterRegistry.close();

        server.disposeNow();
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
        meterRegistry.close();
    }

    @ParameterizedTest
    @EnumSource(StatsdProtocol.class)
    void whenBackendInitiallyDown_metricsSentAfterBackendStarts(StatsdProtocol protocol) throws InterruptedException {
        serverLatch = new CountDownLatch(3);
        // start server to secure an open port
        DisposableChannel server = startServer(protocol, 0);
        final int port = server.address().getPort();
        server.disposeNow();
        meterRegistry = new StatsdMeterRegistry(getUnbufferedConfig(protocol, port), Clock.SYSTEM);
        meterRegistry.start();
        Counter counter = Counter.builder("my.counter").register(meterRegistry);
        IntStream.range(0, 100).forEach(counter::increment);
        server = startServer(protocol, port);
        // client is null until TcpClient first connects
        await().until(() -> meterRegistry.client.get() != null);
        // TcpClient may take some time to reconnect to the server
        await().until(() -> !clientIsDisposed());
        assertThat(serverLatch.getCount()).isEqualTo(3);
        counter.increment();
        counter.increment();
        counter.increment();
        assertThat(serverLatch.await(1, TimeUnit.SECONDS)).isTrue();
        meterRegistry.close();
        server.disposeNow();
    }

    private void startRegistryAndWaitForClient() {
        meterRegistry.start();
        // TODO alternatively, configure the processor to buffer when no subscriber is present... previous behavior used to be this, I think?
        await().until(() -> !clientIsDisposed());
    }

    private boolean clientIsDisposed() {
        return meterRegistry.client.get().isDisposed();
    }

    @NotNull
    private DisposableChannel startServer(StatsdProtocol protocol, int port) {
        if (protocol == StatsdProtocol.UDP) {
            return UdpServer.create()
                    .host("localhost")
                    .port(port)
                    .handle((in, out) ->
                            in.receive().asString()
                                    .flatMap(packet -> {
                                        serverLatch.countDown();
                                        return Flux.never();
                                    }))
                    .wiretap("udpserver", LogLevel.INFO)
                    .bindNow(Duration.ofSeconds(2));
        } else if (protocol == StatsdProtocol.TCP) {
            return TcpServer.create()
                    .host("localhost")
                    .port(port)
                    .handle((in, out) ->
                            in.receive().asString()
                                    .flatMap(packet -> {
                                        IntStream.range(0, packet.split("my.counter").length - 1).forEach(i -> serverLatch.countDown());
                                        in.withConnection(DisposableChannel::dispose);
                                        return Flux.never();
                                    }))
                    .wiretap("tcpserver", LogLevel.INFO)
                    .bindNow(Duration.ofSeconds(5));
        } else {
            throw new IllegalArgumentException("test implementation does not currently support the protocol " + protocol);
        }
    }

    @NotNull
    private StatsdConfig getUnbufferedConfig(StatsdProtocol protocol, int port) {
        return new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
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
