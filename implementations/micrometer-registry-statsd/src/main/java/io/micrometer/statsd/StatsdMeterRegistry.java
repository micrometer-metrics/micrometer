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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
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

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * @author Jon Schneider
 */
public class StatsdMeterRegistry extends MeterRegistry {
    private final StatsdConfig statsdConfig;
    private final HierarchicalNameMapper nameMapper;
    private final Collection<StatsdPollable> pollableMeters = new CopyOnWriteArrayList<>();

    volatile LogbackMetricsSuppressingUnicastProcessor publisher;

    private Disposable.Swap udpClient = Disposables.swap();
    private Disposable.Swap meterPoller = Disposables.swap();

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        this(config, HierarchicalNameMapper.DEFAULT, clock);
    }

    public StatsdMeterRegistry(StatsdConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        super(clock);

        this.statsdConfig = config;
        this.nameMapper = nameMapper;

        switch (statsdConfig.flavor()) {
            case DATADOG:
                config().namingConvention(NamingConvention.dot);
                break;
            case TELEGRAF:
                config().namingConvention(NamingConvention.snakeCase);
                break;
            default:
                config().namingConvention(NamingConvention.camelCase);
        }

        UnicastProcessor<String> processor = UnicastProcessor.create(Queues.<String>get(statsdConfig.queueSize()).get());
        this.publisher = new LogbackMetricsSuppressingUnicastProcessor(processor);

        gauge("statsd.queue.size", this.publisher, LogbackMetricsSuppressingUnicastProcessor::size);
        gauge("statsd.queue.capacity", this.publisher, LogbackMetricsSuppressingUnicastProcessor::getBufferSize);

        if (config.enabled())
            start();
    }

    public void start() {
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

    public void stop() {
        udpClient.dispose();
        meterPoller.dispose();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        StatsdGauge<T> gauge = new StatsdGauge<>(id, lineBuilder(id), publisher, obj, valueFunction, statsdConfig.publishUnchangedMeters());
        pollableMeters.add(gauge);
        return gauge;
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

    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");

    @SuppressWarnings("ConstantConditions")
    @Override
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        Timer timer = new StatsdTimer(id, lineBuilder(id), publisher, clock, histogramConfig, pauseDetector, getBaseTimeUnit(),
                statsdConfig.step().toMillis());

        for (double percentile : histogramConfig.getPercentiles()) {
            switch (statsdConfig.flavor()) {
                case DATADOG:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + "percentile", timer,
                            t -> t.percentile(percentile, getBaseTimeUnit()));
                    break;
                case TELEGRAF:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + ".percentile", timer,
                            t -> t.percentile(percentile, getBaseTimeUnit()));
                    break;
                case ETSY:
                    gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile * 100)),
                            timer, t -> t.percentile(percentile, getBaseTimeUnit()));
                    break;
            }
        }

        if (histogramConfig.isPublishingHistogram()) {
            for (Long bucket : histogramConfig.getHistogramBuckets(false)) {
                more().counter(id.getName() + ".histogram", Tags.concat(getConventionTags(id), "bucket",
                        percentileFormat.format(TimeUtils.nanosToUnit(bucket, TimeUnit.MILLISECONDS))),
                        timer, s -> s.histogramCountAtValue(bucket));
            }
        }

        return timer;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        DistributionSummary summary = new StatsdDistributionSummary(id, lineBuilder(id), publisher, clock, histogramConfig);

        for (double percentile : histogramConfig.getPercentiles()) {
            switch (statsdConfig.flavor()) {
                case DATADOG:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + "percentile", summary,
                            s -> s.percentile(percentile));
                    break;
                case TELEGRAF:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + ".percentile", summary,
                            s -> s.percentile(percentile));
                    break;
                case ETSY:
                    gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile * 100)),
                            summary, s -> s.percentile(percentile));
                    break;
            }
        }

        if (histogramConfig.isPublishingHistogram()) {
            for (Long bucket : histogramConfig.getHistogramBuckets(false)) {
                more().counter(id.getName() + ".histogram", Tags.concat(getConventionTags(id), "bucket",
                        Long.toString(bucket)), summary, s -> s.histogramCountAtValue(bucket));
            }
        }

        return summary;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        StatsdFunctionCounter fc = new StatsdFunctionCounter<>(id, obj, valueFunction, lineBuilder(id), publisher);
        pollableMeters.add(fc);
        return fc;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        StatsdFunctionTimer ft = new StatsdFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits,
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

    private StatsdLineBuilder lineBuilder(Meter.Id id) {
        return new StatsdLineBuilder(id, statsdConfig.flavor(), nameMapper, config());
    }

    @Override
    protected HistogramConfig defaultHistogramConfig() {
        return HistogramConfig.builder()
                .histogramExpiry(statsdConfig.step())
                .build()
                .merge(HistogramConfig.DEFAULT);
    }

    static class LogbackMetricsSuppressingUnicastProcessor implements Processor<String, String> {
        private final UnicastProcessor<String> processor;

        private LogbackMetricsSuppressingUnicastProcessor(UnicastProcessor<String> processor) {
            this.processor = processor;
        }

        @Override
        public void subscribe(Subscriber<? super String> s) {
            processor.subscribe(s);
        }

        @Override
        public void onSubscribe(Subscription s) {
            processor.onSubscribe(s);
        }

        @Override
        public void onNext(String s) {
            LogbackMetrics.ignoreMetrics(() -> processor.onNext(s));
        }

        @Override
        public void onError(Throwable t) {
            LogbackMetrics.ignoreMetrics(() -> processor.onError(t));
        }

        @Override
        public void onComplete() {
            LogbackMetrics.ignoreMetrics(processor::onComplete);
        }

        int size() {
            return processor.size();
        }

        int getBufferSize() {
            return processor.getBufferSize();
        }
    }
}
