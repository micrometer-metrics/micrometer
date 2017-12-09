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
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.instrument.util.TimeUtils;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.udp.UdpClient;
import reactor.util.concurrent.Queues;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static java.util.Optional.ofNullable;

/**
 * @author Jon Schneider
 */
public class StatsdMeterRegistry extends MeterRegistry {
    private final StatsdConfig statsdConfig;

    private final HierarchicalNameMapper nameMapper;

    private volatile UnicastProcessor<String> publisher;

    private Disposable.Swap udpClient = Disposables.swap();

    private final Collection<StatsdPollable> pollableMeters = Collections.synchronizedCollection(new LinkedList<>());

    // VisibleForTesting
    Disposable.Swap meterPoller = Disposables.swap();

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        this(config, null, clock);
    }

    public StatsdMeterRegistry(StatsdConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        super(clock);

        this.statsdConfig = config;
        this.nameMapper = ofNullable(nameMapper).orElse(HierarchicalNameMapper.DEFAULT);

        switch (statsdConfig.flavor()) {
            case Datadog:
                config().namingConvention(NamingConvention.dot);
                break;
            case Telegraf:
                config().namingConvention(NamingConvention.snakeCase);
                break;
            default:
                config().namingConvention(NamingConvention.camelCase);
        }

        this.publisher = UnicastProcessor.create(Queues.<String>get(statsdConfig.queueSize()).get());
        gauge("statsd.queue.size", this.publisher, UnicastProcessor::size);
        gauge("statsd.queue.capacity", this.publisher, UnicastProcessor::getBufferSize);

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

    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");

    @Override
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig) {
        Timer timer = new StatsdTimer(id, lineBuilder(id), publisher, clock, histogramConfig, statsdConfig.step().toMillis());

        for (double percentile : histogramConfig.getPercentiles()) {
            switch (statsdConfig.flavor()) {
                case Datadog:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + "percentile", timer,
                        t -> t.percentile(percentile, getBaseTimeUnit()));
                    break;
                case Telegraf:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + ".percentile", timer,
                        t -> t.percentile(percentile, getBaseTimeUnit()));
                    break;
                case Etsy:
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

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        DistributionSummary summary = new StatsdDistributionSummary(id, lineBuilder(id), publisher, clock, histogramConfig, statsdConfig.step().toMillis());

        for (double percentile : histogramConfig.getPercentiles()) {
            switch (statsdConfig.flavor()) {
                case Datadog:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + "percentile", summary,
                        s -> s.percentile(percentile));
                    break;
                case Telegraf:
                    gauge(id.getName() + "." + percentileFormat.format(percentile * 100) + ".percentile", summary,
                        s -> s.percentile(percentile));
                    break;
                case Etsy:
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
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        StatsdFunctionCounter fc = new StatsdFunctionCounter<>(id, obj, f, lineBuilder(id), publisher);
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
                case Count:
                case Total:
                case TotalTime:
                    pollableMeters.add(() -> publisher.onNext(line.count((long) ms.getValue(), ms.getStatistic())));
                    break;
                case Value:
                case ActiveTasks:
                case Duration:
                case Unknown:
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
}
