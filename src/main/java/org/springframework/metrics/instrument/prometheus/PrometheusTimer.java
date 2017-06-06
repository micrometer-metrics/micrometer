/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.prometheus;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.AbstractTimer;
import org.springframework.metrics.instrument.internal.MeterId;
import org.springframework.metrics.instrument.stats.quantile.Quantiles;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.metrics.instrument.internal.TimeUtils.secondsToUnit;

public class PrometheusTimer extends AbstractTimer {
    private CustomPrometheusSummary.Child summary;

    PrometheusTimer(MeterId id, CustomPrometheusSummary.Child summary, Clock clock) {
        super(id, clock);
        this.summary = summary;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            final double seconds = TimeUnit.NANOSECONDS.convert(amount, unit) / 10e8;

            // Prometheus prefers to receive everything in base units, i.e. seconds
            summary.observe(seconds);
        }
    }

    @Override
    public long count() {
        return summary.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return secondsToUnit(summary.sum(), unit);
    }

    @Override
    public Iterable<Measurement> measure() {
        return summary.measure();
    }
}
