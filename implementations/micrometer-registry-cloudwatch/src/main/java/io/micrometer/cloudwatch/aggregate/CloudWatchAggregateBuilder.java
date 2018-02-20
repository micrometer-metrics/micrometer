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
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;

import java.util.*;
import java.util.stream.Stream;

/**
 * Suppose you have the following series (we'll assume this metric just reports a single value, a count):
 * <p>
 * http.server.requests{method=GET,uri=/api/foo,host=h1}  = 1
 * http.server.requests{method=GET,uri=/api/foo,host=h2}  = 6
 * http.server.requests{method=GET,uri=/api/foo,host=h3}  = 2
 * http.server.requests{method=GET,uri=/api/bar,host=h3}  = 3
 * http.server.requests{method=POST,uri=/api/foo,host=h4} = 4
 * <p>
 * Search should allow you to select a group of time series (say where name = http.server.requests and method = GET).
 * Any tags that are not uniform across the remaining set of meters are dropped and the individual statistics aggregated.
 * <p>
 * This would yield a single aggregate:
 * <p>
 * http.server.requests{method=GET} = 1 + 6 + 2 + 3 = 12
 *
 * @author Jon Schneider
 */
public class CloudWatchAggregateBuilder {
    private final Search search;
    private final boolean alsoPublishDetail;
    private final Set<String> additionalTagsToDrop;

    /**
     * Create a new metric aggregator.
     *
     * @param search               The query used to build the aggregate.
     * @param alsoPublishDetail    Whether the detailed metrics rolled up by the aggregate should also be published.
     * @param additionalTagsToDrop Drop tags with these keys even if the tag value is the same across all matching meters.
     */
    public CloudWatchAggregateBuilder(Search search, boolean alsoPublishDetail, String... additionalTagsToDrop) {
        this.search = search;
        this.alsoPublishDetail = alsoPublishDetail;
        this.additionalTagsToDrop = new HashSet<>(Arrays.asList(additionalTagsToDrop));
    }

    // Generates a set of Meters that are aggregated by name

    @SuppressWarnings("unchecked")
    public Stream<Meter> aggregates() {
        Map<String, Collection<? extends Meter>> metersByName = new HashMap<>();

        for (Meter meter : search.meters()) {
            metersByName.compute(meter.getId().getName(), (name, meters) -> {
                if (meters == null) {
                    meters = new ArrayList<>();
                }
                ((Collection<Meter>) meters).add(meter);
                return meters;
            });
        }

        return metersByName.values().stream()
                .map(meters -> {
                    Meter.Id id = aggregateId(meters);
                    Meter first = meters.iterator().next();
                    if (first instanceof Counter) {
                        return new AggregateCounter(id, (Collection<Counter>) meters);
                    } else if (first instanceof Timer) {
                        return new AggregateTimer(id, (Collection<Timer>) meters);
                    } else if (first instanceof DistributionSummary) {
                        return new AggregateDistributionSummary(id, (Collection<DistributionSummary>) meters);
                    } else if (first instanceof FunctionCounter) {
                        return new AggregateFunctionCounter(id, (Collection<FunctionCounter>) meters);
                    } else if (first instanceof FunctionTimer) {
                        return new AggregateFunctionTimer(id, (Collection<FunctionTimer>) meters);
                    } else if (first instanceof TimeGauge) {
                        return new AggregateTimeGauge(id, (Collection<TimeGauge>) meters);
                    } else if (first instanceof LongTaskTimer) {
                        return new AggregateLongTaskTimer(id, (Collection<LongTaskTimer>) meters);
                    } else if (first instanceof Gauge) {
                        return new AggregateGauge(id, (Collection<Gauge>) meters);
                    } else {
                        return new AggregateCustomMeter(id, (Collection<Meter>) meters);
                    }
                });
    }

    Meter.Id aggregateId(Collection<? extends Meter> meters) {
        Meter.Id firstId = meters.iterator().next().getId();
        Set<Tag> tags = new HashSet<>(firstId.getTags());

        for (Meter meter : meters) {
            tags.retainAll(meter.getId().getTags());
        }

        tags.removeIf(t -> additionalTagsToDrop.contains(t.getKey()));

        return new Meter.Id(firstId.getName(), tags, firstId.getBaseUnit(), firstId.getDescription(), firstId.getType());
    }

    public boolean isAlsoPublishDetail() {
        return alsoPublishDetail;
    }

    public Search getSearch() {
        return search;
    }
}
