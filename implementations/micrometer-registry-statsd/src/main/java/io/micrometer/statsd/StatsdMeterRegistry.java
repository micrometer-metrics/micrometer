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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import io.micrometer.statsd.internal.*;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.udp.UdpClient;
import reactor.util.concurrent.Queues;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * @author Jon Schneider
 */
public class StatsdMeterRegistry extends MeterRegistry {
    private final StatsdConfig statsdConfig;
    private final HierarchicalNameMapper nameMapper;
    private final Collection<StatsdPollable> pollableMeters = new CopyOnWriteArrayList<>();
    Processor<String, String> publisher;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private Disposable.Swap udpClient = Disposables.swap();
    private Disposable.Swap meterPoller = Disposables.swap();

    @Nullable
    private Function<Meter.Id, StatsdLineBuilder> lineBuilderFunction;

    @Nullable
    private Consumer<String> lineSink;

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        this(config, HierarchicalNameMapper.DEFAULT, clock);
    }

    /**
     * Use this constructor for Etsy-flavored StatsD when you need to influence the way Micrometer's dimensional {@link Meter.Id}
     * is written to a flat hierarchical name.
     *
     * @param config     The StatsD configuration.
     * @param nameMapper A strategy for flattening dimensional IDs.
     * @param clock      The clock to use for timing and polling certain types of meters.
     */
    public StatsdMeterRegistry(StatsdConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        this(config, nameMapper, namingConventionFromFlavor(config.flavor()), clock, null, null);
    }

    public static StatsdMeterRegistry.Builder builder(StatsdConfig config) {
        return new Builder(config);
    }

    /**
     * A builder for configuration of less common knobs on {@link StatsdMeterRegistry}.
     */
    @Incubating(since = "1.0.1")
    public static class Builder {
        private final StatsdConfig config;

        private Clock clock = Clock.SYSTEM;
        private NamingConvention namingConvention;
        private HierarchicalNameMapper nameMapper = HierarchicalNameMapper.DEFAULT;

        @Nullable
        private Function<Meter.Id, StatsdLineBuilder> lineBuilderFunction = null;

        @Nullable
        private Consumer<String> lineSink;

        public Builder(StatsdConfig config) {
            this.config = config;
            this.namingConvention = namingConventionFromFlavor(config.flavor());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Used for completely customizing the StatsD line format. Intended for use by custom, proprietary
         * StatsD flavors.
         *
         * @param lineBuilderFunction A mapping from a meter ID to a StatsD line generator that knows how to write counts, gauges
         *                            timers, and histograms in the proprietary format.
         * @return This builder.
         */
        public Builder lineBuilder(Function<Meter.Id, StatsdLineBuilder> lineBuilderFunction) {
            this.lineBuilderFunction = lineBuilderFunction;
            return this;
        }

        public Builder nameMapper(HierarchicalNameMapper nameMapper) {
            this.nameMapper = nameMapper;
            return this;
        }

        public Builder lineSink(Consumer<String> lineSink) {
            this.lineSink = lineSink;
            return this;
        }

        public StatsdMeterRegistry build() {
            return new StatsdMeterRegistry(config, nameMapper, namingConvention, clock, lineBuilderFunction, lineSink);
        }
    }

    private StatsdMeterRegistry(StatsdConfig config,
                                HierarchicalNameMapper nameMapper,
                                NamingConvention namingConvention,
                                Clock clock,
                                @Nullable Function<Meter.Id, StatsdLineBuilder> lineBuilderFunction,
                                @Nullable Consumer<String> lineSink) {
        super(clock);

        this.statsdConfig = config;
        this.nameMapper = nameMapper;
        this.lineBuilderFunction = lineBuilderFunction;
        this.lineSink = lineSink;
        config().namingConvention(namingConvention);

        UnicastProcessor<String> processor = UnicastProcessor.create(Queues.<String>unboundedMultiproducer().get());

        try {
            Class.forName("ch.qos.logback.classic.turbo.TurboFilter", false, getClass().getClassLoader());
            this.publisher = new LogbackMetricsSuppressingUnicastProcessor(processor);
        } catch (ClassNotFoundException e) {
            this.publisher = processor;
        }

        if (lineSink != null) {
            publisher.subscribe(new Subscriber<String>() {
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

            // now that we're connected, start polling gauges and other pollable meter types
            meterPoller.replace(Flux.interval(statsdConfig.pollingFrequency())
                    .doOnEach(n -> pollableMeters.forEach(StatsdPollable::poll))
                    .subscribe());
        }

        if (config.enabled())
            start();
    }

    public void start() {
        if (started.compareAndSet(false, true) && lineSink == null) {
            UdpClient.create(statsdConfig.host(), statsdConfig.port())
                    .newHandler((in, out) -> out
                            .options(NettyPipeline.SendOptions::flushOnEach)
                            .sendString(publisher)
                            .neverComplete()
                    )
                    .subscribe(client -> {
                        this.udpClient.replace(client);

                        // now that we're connected, start polling gauges and other pollable meter types
                        meterPoller.replace(Flux.interval(statsdConfig.pollingFrequency())
                                .doOnEach(n -> pollableMeters.forEach(StatsdPollable::poll))
                                .subscribe());
                    });
        }
    }

    public void stop() {
        if (started.compareAndSet(true, false)) {
            udpClient.dispose();
            meterPoller.dispose();
        }
    }

    @Override
    public void close() {
        pollableMeters.forEach(StatsdPollable::poll);
        stop();
        super.close();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        StatsdGauge<T> gauge = new StatsdGauge<>(id, lineBuilder(id), publisher, obj, valueFunction, statsdConfig.publishUnchangedMeters());
        pollableMeters.add(gauge);
        return gauge;
    }

    private StatsdLineBuilder lineBuilder(Meter.Id id) {
        if (lineBuilderFunction == null) {
            lineBuilderFunction = id2 -> {
                switch (statsdConfig.flavor()) {
                    case DATADOG:
                        return new DatadogStatsdLineBuilder(id2, config());
                    case TELEGRAF:
                        return new TelegrafStatsdLineBuilder(id2, config());
                    case SYSDIG:
                        return new SysdigStatsdLineBuilder(id2, config());
                    case ETSY:
                    default:
                        return new EtsyStatsdLineBuilder(id2, config(), nameMapper);
                }
            };
        }
        return lineBuilderFunction.apply(id);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StatsdCounter(id, lineBuilder(id), publisher);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        StatsdLongTaskTimer ltt = new StatsdLongTaskTimer(id, lineBuilder(id), publisher, clock, statsdConfig.publishUnchangedMeters());
        pollableMeters.add(ltt);
        return ltt;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector
            pauseDetector) {
        Timer timer = new StatsdTimer(id, lineBuilder(id), publisher, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                statsdConfig.step().toMillis());
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig
            distributionStatisticConfig, double scale) {
        DistributionSummary summary = new StatsdDistributionSummary(id, lineBuilder(id), publisher, clock, distributionStatisticConfig, scale);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        StatsdFunctionCounter fc = new StatsdFunctionCounter<>(id, obj, countFunction, lineBuilder(id), publisher);
        pollableMeters.add(fc);
        return fc;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T
            obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit
                                                         totalTimeFunctionUnit) {
        StatsdFunctionTimer ft = new StatsdFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit,
                getBaseTimeUnit(), lineBuilder(id), publisher);
        pollableMeters.add(ft);
        return ft;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> {
            StatsdLineBuilder line = lineBuilder(id);
            switch (ms.getStatistic()) {
                case COUNT:
                case TOTAL:
                case TOTAL_TIME:
                    pollableMeters.add(() -> publisher.onNext(line.count((long) ms.getValue(), ms.getStatistic())));
                    break;
                case VALUE:
                case ACTIVE_TASKS:
                case DURATION:
                case UNKNOWN:
                    pollableMeters.add(() -> publisher.onNext(line.gauge(ms.getValue(), ms.getStatistic())));
                    break;
            }
        });
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(statsdConfig.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    public int queueSize() {
        try {
            return (Integer) publisher.getClass().getMethod("size").invoke(publisher);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // should never happen
            return 0;
        }
    }

    public int queueCapacity() {
        try {
            return (Integer) publisher.getClass().getMethod("getBufferSize").invoke(publisher);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // should never happen
            return 0;
        }
    }

    private static NamingConvention namingConventionFromFlavor(StatsdFlavor flavor) {
        switch (flavor) {
            case DATADOG:
            case SYSDIG:
                return NamingConvention.dot;
            case TELEGRAF:
                return NamingConvention.snakeCase;
            default:
                return NamingConvention.camelCase;
        }
    }
}
