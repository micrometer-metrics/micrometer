package io.micrometer.statsd;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.logging.LoggingHandler;
import org.reactivestreams.Processor;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.channel.ChannelOperations;
import reactor.ipc.netty.channel.ContextHandler;
import reactor.ipc.netty.options.ClientOptions;
import reactor.ipc.netty.tcp.TcpResources;
import reactor.util.concurrent.Queues;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * @author Jon Schneider
 */
public class StatsdMeterRegistry extends AbstractMeterRegistry {
    private final StatsdConfig statsdConfig;

    private volatile Processor<String, String> publisher;

    private Disposable.Swap udpClient = Disposables.swap();

    private final Collection<StatsdPollable> pollableMeters = Collections.synchronizedCollection(new LinkedList<>());

    // VisibleForTesting
    Disposable.Swap meterPoller = Disposables.swap();

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        super(clock);

        this.statsdConfig = config;
        config().namingConvention(NamingConvention.camelCase);

        this.publisher = DirectProcessor.create();

        if (config.enabled())
            start();
    }

    public void start() {
        this.publisher = UnicastProcessor.create(Queues.<String>get(statsdConfig.queueSize()).get());

        Mono
            .<NettyContext>create(sink -> {
                ClientOptions options = new ClientOptions(ClientOptions.builder()
                    .loopResources(TcpResources.get())
                    .poolResources(TcpResources.get())) {
                    @Override
                    protected boolean useDatagramChannel() {
                        return true;
                    }
                };

                Bootstrap b = options.get();

                SocketAddress adr = new InetSocketAddress(statsdConfig.host(), statsdConfig.port());
                b.remoteAddress(adr);

                ContextHandler<?> h = ContextHandler.newClientContext(sink,
                    options,
                    new LoggingHandler(StatsdMeterRegistry.class),
                    false,
                    adr,
                    (ch, c, msg) -> ChannelOperations.bind(ch, (in, out) -> {
                        out.options(NettyPipeline.SendOptions::flushOnEach)
                            .sendString(publisher)
                            .then().subscribe();
                        return Flux.never();
                    }, c));

                b.handler(h);
                h.setFuture(b.connect());
            })
            .subscribe(client -> {
                this.udpClient.replace(client);

                // now that we're connected, start polling gauges
                meterPoller.replace(Flux.interval(statsdConfig.pollingFrequency())
                    .doOnEach(n -> {
                        synchronized (pollableMeters) {
                            pollableMeters.forEach(StatsdPollable::poll);
                        }
                    })
                    .subscribe());
            });
    }

    public void stop() {
        udpClient.dispose();
        meterPoller.dispose();
        this.publisher = DirectProcessor.create();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        StatsdGauge<T> gauge = new StatsdGauge<>(id, lineBuilder(id), publisher, obj, f);
        pollableMeters.add(gauge);
        return gauge;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StatsdCounter(id, lineBuilder(id), publisher);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        StatsdLongTaskTimer ltt = new StatsdLongTaskTimer(id, lineBuilder(id), publisher, clock);
        pollableMeters.add(ltt);
        return ltt;
    }

    @Override
    protected Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return new StatsdTimer(id, lineBuilder(id), publisher, clock, quantiles,
            histogram == null ? null : histogram.create(Histogram.Summation.Normal));
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return null;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
    }

    @Override
    protected <T> Gauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        return null;
    }

    @Override
    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return null;
    }

    private StatsdLineBuilder lineBuilder(Meter.Id id) {
        return new StatsdLineBuilder(id, statsdConfig.flavor(), config().namingConvention());
    }
}
