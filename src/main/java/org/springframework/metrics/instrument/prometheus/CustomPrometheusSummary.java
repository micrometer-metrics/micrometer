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

import com.google.common.collect.Streams;
import io.prometheus.client.Collector;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.stats.Quantiles;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Necessitated by a desire to offer different quantile algorithms.
 *
 * @author Jon Schneider
 */
public class CustomPrometheusSummary extends Collector {
    private final String name;
    private final String countName;
    private final String sumName;
    private final List<String> tagKeys;

    private final Collection<Child> children = new ConcurrentLinkedQueue<>();

    CustomPrometheusSummary(String name, Iterable<Tag> tags) {
        this.name = name;
        this.countName = name + "_count";
        this.sumName = name + "_sum";
        this.tagKeys = Streams.stream(tags).map(Tag::getKey).collect(toList());
    }

    public Child child(Iterable<Tag> tags, Quantiles quantiles) {
        Child child = new Child(tags, quantiles);
        children.add(child);
        return child;
    }

    class Child {
        private final List<String> tagValues;
        private final Quantiles quantiles;
        private List<String> quantileKeys;

        private LongAdder count = new LongAdder();
        private DoubleAdder sum = new DoubleAdder();

        Child(Iterable<Tag> tags, Quantiles quantiles) {
            this.quantiles = quantiles;
            this.tagValues = Streams.stream(tags).map(Tag::getValue).collect(toList());

            if(quantiles != null) {
                quantileKeys = new LinkedList<>(tagKeys);
                quantileKeys.add("quantile");
            }
        }

        public Stream<MetricFamilySamples.Sample> collect() {
            Stream.Builder<MetricFamilySamples.Sample> samples = Stream.builder();

            if(quantiles != null) {
                for(Double q: quantiles.monitored()) {
                    List<String> quantileValues = new LinkedList<>(tagValues);
                    quantileValues.add(Collector.doubleToGoString(q));
                    samples.add(new MetricFamilySamples.Sample(name, quantileKeys, quantileValues, quantiles.get(q)));
                }
            }

            samples.add(new MetricFamilySamples.Sample(countName, tagKeys, tagValues, count.sum()));
            samples.add(new MetricFamilySamples.Sample(sumName, tagKeys, tagValues, sum.sum()));

            return samples.build();
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

    @Override
    public List<MetricFamilySamples> collect() {
        return Collections.singletonList(new MetricFamilySamples(name, Type.SUMMARY, "", children.stream()
            .flatMap(Child::collect).collect(toList())));
    }
}
