/**
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import reactor.core.publisher.Sinks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StatsdLongTaskTimer extends DefaultLongTaskTimer implements StatsdPollable {
    private final StatsdLineBuilder lineBuilder;
    private final Sinks.SinksMultiproducer<String> sink;

    private final AtomicReference<Long> lastActive = new AtomicReference<>(Long.MIN_VALUE);
    private final AtomicReference<Double> lastDuration = new AtomicReference<>(Double.NEGATIVE_INFINITY);

    private final boolean alwaysPublish;

    StatsdLongTaskTimer(Id id, StatsdLineBuilder lineBuilder, Sinks.SinksMultiproducer<String> sink, Clock clock, boolean alwaysPublish,
                        DistributionStatisticConfig distributionStatisticConfig, TimeUnit baseTimeUnit) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, false);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
        this.alwaysPublish = alwaysPublish;
    }

    @Override
    public void poll() {
        long active = activeTasks();
        if (alwaysPublish || lastActive.getAndSet(active) != active) {
            sink.trySubmitNext(lineBuilder.gauge(active, Statistic.ACTIVE_TASKS));
        }

        double duration = duration(TimeUnit.MILLISECONDS);
        if (alwaysPublish || lastDuration.getAndSet(duration) != duration) {
            sink.trySubmitNext(lineBuilder.gauge(duration, Statistic.DURATION));
        }

        double max = max(TimeUnit.MILLISECONDS);
        if (alwaysPublish || lastDuration.getAndSet(duration) != duration) {
            sink.trySubmitNext(lineBuilder.gauge(max, Statistic.MAX));
        }
    }
}
