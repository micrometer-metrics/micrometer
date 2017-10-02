package io.micrometer.statsd;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.statsd.internal.MemoizingSupplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.ipc.netty.udp.UdpClient;
import reactor.util.concurrent.Queues;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

public class StatsdMeterRegistry extends AbstractMeterRegistry {
    private final StatsdConfig statsdConfig;
    private final UnicastProcessor<String> publisher;

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        super(clock);
        this.statsdConfig = config;
        this.publisher = UnicastProcessor.create(Queues.<String>get(statsdConfig.queueSize()).get());
        this.config().namingConvention(NamingConvention.camelCase);

        UdpClient.create(statsdConfig.host(), statsdConfig.port())
            .newHandler((in, out) -> {
                out.sendString(publisher);
                return Flux.never();
            });
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return null;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StatsdCounter(id, new Writer(id));
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return null;
    }

    @Override
    protected Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return null;
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

    class Writer {
        private final Meter.Id id;

        private Supplier<String> datadogTagString;
        private Supplier<String> telegrafTagString;

        public Writer(Meter.Id id) {
            this.id = id;

            // |#tag1:value1,tag2,tag3:value3
            this.datadogTagString = new MemoizingSupplier<>(() ->
                id.getTags().iterator().hasNext() ?
                    "|#" + stream(id.getTags().spliterator(), false)
                        .map(t -> t.getKey() + ":" + t.getValue())
                        .collect(Collectors.joining(","))
                    : ""
            );

            // ,service=payroll,region=us-west
            this.telegrafTagString = new MemoizingSupplier<>(() ->
                id.getTags().iterator().hasNext() ?
                    "," + stream(id.getTags().spliterator(), false)
                        .map(t -> t.getKey() + "=" + t.getValue())
                        .collect(Collectors.joining(","))
                    : ""
            );
        }

        void count(long amount) {
            NamingConvention convention = config().namingConvention();
            switch (statsdConfig.flavor()) {
                case Etsy:
                    publisher.onNext(HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, convention) + ":" + amount + "|c");
                    break;
                case Datadog:
                    publisher.onNext(HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, convention) + ":" + amount + "|c" + datadogTagString);
                    break;
                case Telegraf:
                    publisher.onNext(convention.name(id.getName(), Meter.Type.Counter, id.getBaseUnit()) + telegrafTagString + ":" + amount + "|c");
                    break;
            }
        }
    }
}
