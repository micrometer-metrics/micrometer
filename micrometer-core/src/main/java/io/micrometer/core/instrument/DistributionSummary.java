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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Arrays;

/**
 * Track the sample distribution of events. An example would be the response sizes for requests
 * hitting and http server.
 */
public interface DistributionSummary extends Meter {

    /**
     * Updates the statistics kept by the summary with the specified amount.
     *
     * @param amount Amount for an event being measured. For example, if the size in bytes of responses
     *               from a server. If the amount is less than 0 the value will be dropped.
     */
    void record(double amount);

    /**
     * The number of times that record has been called since this timer was created.
     */
    long count();

    /**
     * The total amount of all recorded events since this summary was created.
     */
    double totalAmount();

    interface Builder {
        Builder quantiles(Quantiles quantiles);

        Builder histogram(Histogram.Builder<?> histogram);

        Builder tags(Iterable<Tag> tags);
        default Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        Builder description(String description);

        Builder baseUnit(String unit);

        DistributionSummary create();
    }

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(this::totalAmount, Statistic.Total)
        );
    }
}