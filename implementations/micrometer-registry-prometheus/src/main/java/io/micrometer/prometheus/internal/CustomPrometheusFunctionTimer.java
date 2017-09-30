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
package io.micrometer.prometheus.internal;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Tag;
import io.prometheus.client.Collector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class CustomPrometheusFunctionTimer extends Collector {
    private final PrometheusCollectorId id;
    private final String countName;
    private final String sumName;

    private final Collection<Child> children = new ConcurrentLinkedQueue<>();

    public CustomPrometheusFunctionTimer(PrometheusCollectorId id) {
        this.id = id;
        this.countName = id.getName() + "_count";
        this.sumName = id.getName() + "_sum";
    }

    public Child child(Iterable<Tag> tags, FunctionTimer timer) {
        Child child = new Child(tags, timer);
        children.add(child);
        return child;
    }

    public class Child implements CustomCollectorChild {
        private final List<String> tagValues;
        private final FunctionTimer timer;

        Child(Iterable<Tag> tags, FunctionTimer timer) {
            this.tagValues = stream(tags.spliterator(), false).map(Tag::getValue).collect(toList());
            this.timer = timer;
        }

        @Override
        public Stream<MetricFamilySamples.Sample> collect() {
            Stream.Builder<MetricFamilySamples.Sample> samples = Stream.builder();

            samples.add(new MetricFamilySamples.Sample(countName, id.getTagKeys(), tagValues, timer.count()));
            samples.add(new MetricFamilySamples.Sample(sumName, id.getTagKeys(), tagValues, timer.totalTime(TimeUnit.SECONDS)));

            return samples.build();
        }
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return Collections.singletonList(new MetricFamilySamples(id.getName(), Type.SUMMARY, id.getDescription(), children.stream()
            .flatMap(Child::collect).collect(toList())));
    }
}
