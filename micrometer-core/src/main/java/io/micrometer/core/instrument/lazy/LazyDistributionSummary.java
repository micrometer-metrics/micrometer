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
package io.micrometer.core.instrument.lazy;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;

import java.util.function.Supplier;

public class LazyDistributionSummary implements DistributionSummary {
    private final Supplier<DistributionSummary> summaryBuilder;
    private volatile DistributionSummary summary;

    private DistributionSummary summary() {
        final DistributionSummary result = summary;
        return result == null ? (summary == null ? summary = summaryBuilder.get() : summary) : result;
    }

    public LazyDistributionSummary(Supplier<DistributionSummary> summaryBuilder) {
        this.summaryBuilder = summaryBuilder;
    }

    @Override
    public String getName() {
        return summary().getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return summary().getTags();
    }

    @Override
    public String getDescription() {
        return summary().getDescription();
    }

    @Override
    public void record(double amount) {
        summary().record(amount);
    }

    @Override
    public long count() {
        return summary().count();
    }

    @Override
    public double totalAmount() {
        return summary().totalAmount();
    }
}
