/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class StatsdFunctionTimer<T> extends CumulativeFunctionTimer<T> implements StatsdPollable {

    private final StatsdLineBuilder lineBuilder;

    private final FluxSink<String> sink;

    private final AtomicReference<Long> lastCount = new AtomicReference<>(0L);

    private final AtomicReference<Double> lastTime = new AtomicReference<>(0.0);

    StatsdFunctionTimer(Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction,
            TimeUnit totalTimeFunctionUnit, TimeUnit baseTimeUnit, StatsdLineBuilder lineBuilder,
            FluxSink<String> sink) {
        super(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, baseTimeUnit);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
    }

    @Override
    public void poll() {
        lastCount.updateAndGet(prevCount -> {
            long count = (long) count();
            long newTimingsCount = count - prevCount;

            if (newTimingsCount > 0) {
                lastTime.updateAndGet(prevTime -> {
                    double totalTime = totalTime(TimeUnit.MILLISECONDS);
                    double newTimingsSum = totalTime - prevTime;

                    // We can't know what the individual timing samples were, so we
                    // approximate each one
                    // by calculating the average of the sum of all new timings seen by
                    // the number of new timing
                    // occurrences.
                    double timingAverage = newTimingsSum / newTimingsCount;
                    for (int i = 0; i < newTimingsCount; i++) {
                        sink.next(lineBuilder.timing(timingAverage));
                    }

                    return totalTime;
                });
            }
            return count;
        });
    }

}
