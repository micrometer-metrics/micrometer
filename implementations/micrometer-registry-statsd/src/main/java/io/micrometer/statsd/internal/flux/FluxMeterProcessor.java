/*
 * Copyright 2017 VMware, Inc.
 * Copyright 2022 Spaceteams GmbH
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

package io.micrometer.statsd.internal.flux;

import io.micrometer.common.lang.Nullable;
import io.micrometer.statsd.internal.MeterProcessor;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdProtocol;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.udp.UdpClient;
import reactor.util.context.Context;
import reactor.util.context.ContextView;
import reactor.util.retry.Retry;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

public class FluxMeterProcessor implements MeterProcessor {

    private StatsdConfig statsdConfig;

    private FluxProcessor<String, String> processor;

    private FluxSink<String> sink = new NoopFluxSink();

    private Disposable.Swap statsdConnection = Disposables.swap();

    private Disposable.Swap meterPoller = Disposables.swap();

    private Consumer<String> lineSink;

    private Runnable pollFunction;

    private final AtomicBoolean started = new AtomicBoolean();

    private static final AttributeKey<Boolean> CONNECTION_DISPOSED = AttributeKey.valueOf("doOnDisconnectCalled");

    public FluxMeterProcessor(StatsdConfig statsdConfig) {
        this(statsdConfig, null, null);
    }

    public FluxMeterProcessor(StatsdConfig statsdConfig, @Nullable Consumer<String> lineSink,
            @Nullable FluxProcessor<String, String> processor) {
        this.statsdConfig = statsdConfig;
        this.lineSink = lineSink;

        if (processor == null) {
            this.processor = DirectProcessor.create();
        }
        else {
            this.processor = processor;
        }

        this.sink = this.processor.sink();

        try {
            Class.forName("ch.qos.logback.classic.turbo.TurboFilter", false, getClass().getClassLoader());
            this.sink = new LogbackMetricsSuppressingFluxSink(this.sink);
        }
        catch (ClassNotFoundException ignore) {
        }
    }

    private void prepareUdpClient(Publisher<String> publisher, Supplier<SocketAddress> remoteAddress) {
        AtomicReference<UdpClient> udpClientReference = new AtomicReference<>();
        UdpClient udpClient = UdpClient.create().remoteAddress(remoteAddress)
                .handle((in, out) -> out.sendString(publisher).neverComplete().retryWhen(
                        Retry.indefinitely().filter(throwable -> throwable instanceof PortUnreachableException)))
                .doOnDisconnected(connection -> {
                    Boolean connectionDisposed = connection.channel().attr(CONNECTION_DISPOSED).getAndSet(Boolean.TRUE);
                    if (connectionDisposed == null || !connectionDisposed) {
                        connectAndSubscribe(udpClientReference.get());
                    }
                });
        udpClientReference.set(udpClient);
        connectAndSubscribe(udpClient);
    }

    private void prepareTcpClient(Publisher<String> publisher) {
        AtomicReference<TcpClient> tcpClientReference = new AtomicReference<>();
        TcpClient tcpClient = TcpClient.create().host(statsdConfig.host()).port(statsdConfig.port())
                .handle((in, out) -> out.sendString(publisher).neverComplete()).doOnDisconnected(connection -> {
                    Boolean connectionDisposed = connection.channel().attr(CONNECTION_DISPOSED).getAndSet(Boolean.TRUE);
                    if (connectionDisposed == null || !connectionDisposed) {
                        connectAndSubscribe(tcpClientReference.get());
                    }
                });
        tcpClientReference.set(tcpClient);
        connectAndSubscribe(tcpClient);
    }

    private void connectAndSubscribe(TcpClient tcpClient) {
        retryReplaceClient(Mono.defer(() -> {
            if (started.get()) {
                return tcpClient.connect();
            }
            return Mono.empty();
        }));
    }

    private void connectAndSubscribe(UdpClient udpClient) {
        retryReplaceClient(Mono.defer(() -> {
            if (started.get()) {
                return udpClient.connect();
            }
            return Mono.empty();
        }));
    }

    private void retryReplaceClient(Mono<? extends Connection> connectMono) {
        connectMono.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofMinutes(1)))
                .subscribe(connection -> {
                    this.statsdConnection.replace(connection);

                    // now that we're connected, start polling gauges and other pollable
                    // meter types
                    startPolling();
                });
    }

    private void poll() {
        if (pollFunction != null) {
            pollFunction.run();
        }
    }

    public void start(Runnable poll) {
        if (started.compareAndSet(false, true)) {
            pollFunction = poll;

            if (lineSink != null) {
                this.processor.subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String line) {
                        if (started.get()) {
                            lineSink.accept(line);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onComplete() {
                        meterPoller.dispose();
                    }
                });

                startPolling();
            }
            else {
                final Publisher<String> publisher;
                if (statsdConfig.buffered()) {
                    publisher = BufferingFlux.create(Flux.from(this.processor), "\n", statsdConfig.maxPacketLength(),
                            statsdConfig.pollingFrequency().toMillis()).onBackpressureLatest();
                }
                else {
                    publisher = this.processor;
                }
                if (statsdConfig.protocol() == StatsdProtocol.UDP) {
                    prepareUdpClient(publisher,
                            () -> InetSocketAddress.createUnresolved(statsdConfig.host(), statsdConfig.port()));
                }
                else if (statsdConfig.protocol() == StatsdProtocol.UDS_DATAGRAM) {
                    prepareUdpClient(publisher, () -> new DomainSocketAddress(statsdConfig.host()));
                }
                else if (statsdConfig.protocol() == StatsdProtocol.TCP) {
                    prepareTcpClient(publisher);
                }
            }
        }
    }

    public void stop() {
        if (started.compareAndSet(true, false)) {
            if (statsdConnection.get() != null) {
                statsdConnection.get().dispose();
            }
            if (meterPoller.get() != null) {
                meterPoller.get().dispose();
            }
        }
    }

    private void startPolling() {
        meterPoller.update(Flux.interval(statsdConfig.pollingFrequency()).doOnEach(n -> poll()).subscribe());
    }

    public void next(String line) {
        this.sink.next(line);
    }

    private static final class NoopFluxSink implements FluxSink<String> {

        @Override
        public FluxSink<String> next(String s) {
            return this;
        }

        @Override
        public void complete() {
        }

        @Override
        public void error(Throwable e) {
        }

        @Override
        public Context currentContext() {
            return Context.empty();
        }

        @Override
        public ContextView contextView() {
            return Context.empty();
        }

        @Override
        public long requestedFromDownstream() {
            return 0;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public FluxSink<String> onRequest(LongConsumer consumer) {
            return this;
        }

        @Override
        public FluxSink<String> onCancel(Disposable d) {
            return this;
        }

        @Override
        public FluxSink<String> onDispose(Disposable d) {
            return this;
        }

    }

    // VisibleForTesting
    public Disposable getConnection() {
        return this.statsdConnection.get();
    }

}
