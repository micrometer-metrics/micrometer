package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;
import org.reactivestreams.Processor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class StatsdTimer extends AbstractTimer implements Timer {
    private LongAdder count = new LongAdder();
    private LongAdder totalTime = new LongAdder();

    private final StatsdLineBuilder writer;
    private final Processor<String, String> publisher;
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    StatsdTimer(Id id, StatsdLineBuilder writer, Processor<String, String> publisher, Clock clock, Quantiles quantiles, Histogram<?> histogram) {
        super(id, clock);
        this.writer = writer;
        this.publisher = publisher;
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            count.increment();

            long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
            totalTime.add(nanoAmount);

            publisher.onNext(writer.count(1) + "\n" + writer.count(nanoAmount, Statistic.TotalTime));

            // FIXME ship these
            if (quantiles != null)
                quantiles.observe(nanoAmount);
            if (histogram != null)
                histogram.observe(nanoAmount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }
}
