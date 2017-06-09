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
package org.springframework.metrics.instrument;

import org.springframework.metrics.instrument.stats.hist.Histogram;
import org.springframework.metrics.instrument.stats.quantile.Quantiles;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer intended to track of a large number of short running events. Example would be something like
 * an HTTP request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 */
public interface Timer extends Meter {
    /**
     * Updates the statistics kept by the counter with the specified amount.
     *
     * @param amount Duration of a single event being measured by this timer. If the amount is less than 0
     *               the value will be dropped.
     * @param unit   Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Executes the Supplier `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    <T> T record(Supplier<T> f);

    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    <T> T recordCallable(Callable<T> f) throws Exception;

    /**
     * Executes the runnable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     */
    void record(Runnable f);

    /**
     * Wrap a {@link Runnable} so that it is timed when invoked.
     *
     * @param f The Runnable to time when it is invoked.
     * @return The wrapped Runnable.
     */
    default Runnable wrap(Runnable f) {
        return () -> record(f);
    }

    /**
     * Wrap a {@link Callable} so that it is timed when invoked.
     *
     * @param f The Callable to time when it is invoked.
     * @return The wrapped Callable.
     */
    <T> Callable<T> wrap(Callable<T> f);

    /**
     * The number of times that record has been called since this timer was created.
     */
    long count();

    /**
     * The total time of all recorded events since this timer was created.
     */
    double totalTime(TimeUnit unit);

    /**
     * The total time in nanoseconds of all recorded events since this timer was created.
     */
    default double totalTimeNanos() {
        return totalTime(TimeUnit.NANOSECONDS);
    }

    interface Builder {
        Builder quantiles(Quantiles quantiles);

        Builder histogram(Histogram<?> histogram);

        Builder tags(Iterable<Tag> tags);
        default Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        Timer create();
    }

    @Override
    default Type getType() {
        return Type.Timer;
    }
}
