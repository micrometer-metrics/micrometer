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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;
import org.reactivestreams.Processor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class StatsdTimer extends AbstractTimer implements Timer {
    private LongAdder count = new LongAdder();
    private DoubleAdder totalTime = new DoubleAdder();

    private final StatsdLineBuilder lineBuilder;
    private final Processor<String, String> publisher;
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    StatsdTimer(Id id, StatsdLineBuilder lineBuilder, Processor<String, String> publisher, Clock clock, Quantiles quantiles, Histogram<?> histogram) {
        super(id, clock);
        this.lineBuilder = lineBuilder;
        this.publisher = publisher;
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            count.increment();

            double msAmount = TimeUtils.convert(amount, unit, TimeUnit.MILLISECONDS);
            totalTime.add(msAmount);

            publisher.onNext(lineBuilder.timing(msAmount));

            if (quantiles != null)
                quantiles.observe(msAmount);
            if (histogram != null)
                histogram.observe(msAmount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.millisToUnit(totalTime.doubleValue(), unit);
    }
}
