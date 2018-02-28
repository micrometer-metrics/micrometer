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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class AggregateCustomMeter extends AggregateMeter {
    private Collection<Meter> meters;

    AggregateCustomMeter(Id aggregateId, Collection<Meter> meters) {
        super(aggregateId);
        this.meters = meters;
    }

    @Override
    public Iterable<Measurement> measure() {
        Map<Statistic, Double> combinedStats = null;

        for (Meter meter : meters) {
            Map<Statistic, Double> stats = new HashMap<>();

            for (Measurement measurement : meter.measure()) {
                stats.put(measurement.getStatistic(), measurement.getValue());
            }

            if (combinedStats == null) {
                combinedStats = stats;
            } else {
                combinedStats.keySet().retainAll(stats.keySet());

                for (Map.Entry<Statistic, Double> statAndValue : combinedStats.entrySet()) {
                    Statistic stat = statAndValue.getKey();
                    combinedStats.put(stat, statAndValue.getValue() + stats.get(stat));
                }
            }
        }

        return combinedStats.entrySet().stream()
                .map(en -> new Measurement(en::getValue, en.getKey()))
                .collect(toList());
    }
}