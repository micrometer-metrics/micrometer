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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    static Builder builder(String name) {
        return new Builder(name);
    }

    class Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram.Builder<?> histogram;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private Builder(String name) {
            this.name = name;
        }

        public Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public Builder histogram(Histogram.Builder<?> histogram) {
            this.histogram = histogram;
            return this;
        }

        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        public DistributionSummary register(MeterRegistry registry) {
            return registry.summary(registry.createId(name, tags, description, baseUnit), histogram, quantiles);
        }
    }

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(this::totalAmount, Statistic.Total)
        );
    }
}