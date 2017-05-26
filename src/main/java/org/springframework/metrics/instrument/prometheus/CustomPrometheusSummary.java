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

import io.prometheus.client.Collector;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.stats.Quantiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Necessitated by a desire to offer different quantile algorithms.
 *
 * @author Jon Schneider
 */
public class CustomPrometheusSummary extends Collector {
    private final String name;
    private final String countName;
    private final String sumName;

    private final Quantiles quantiles;

    private List<String> tagKeys = new ArrayList<>();
    private List<String> tagValues = new ArrayList<>();
    private List<String> quantileKeys;

    private LongAdder count = new LongAdder();
    private DoubleAdder sum = new DoubleAdder();

    CustomPrometheusSummary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        this.name = name;
        this.countName = name + "_count";
        this.sumName = name + "_sum";

        this.quantiles = quantiles;

        for (Tag tag : tags) {
            tagKeys.add(tag.getKey());
            tagValues.add(tag.getValue());
        }

        if(quantiles != null) {
            quantileKeys = new LinkedList<>(tagKeys);
            quantileKeys.add("quantile");
        }
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();

        if(quantiles != null) {
            for(Double q: quantiles.monitored()) {
                List<String> quantileValues = new LinkedList<>(tagValues);
                quantileValues.add(Collector.doubleToGoString(q));
                samples.add(new MetricFamilySamples.Sample(name, quantileKeys, quantileValues, quantiles.get(q)));
            }
        }

        samples.add(new MetricFamilySamples.Sample(countName, tagKeys, tagValues, count.sum()));
        samples.add(new MetricFamilySamples.Sample(sumName, tagKeys, tagValues, sum.sum()));

        MetricFamilySamples summarySample = new MetricFamilySamples(name, Type.SUMMARY, "", samples);
        return Collections.singletonList(summarySample);
    }

    public void observe(double amt) {
        count.add(1);
        sum.add(amt);
        if (quantiles != null) {
            quantiles.observe(amt);
        }
    }

    public long count() {
        return count.sum();
    }

    public double sum() {
        return sum.sum();
    }
}
