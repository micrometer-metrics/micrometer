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
package io.micrometer.core.instrument.prometheus.internal;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Tag;
import io.prometheus.client.Collector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class CustomPrometheusCollector extends Collector {
    private final String name;
    private final Type type;
    private final List<String> tagKeys;
    private final Collection<Child> children = new ConcurrentLinkedQueue<>();

    public CustomPrometheusCollector(String name, Iterable<Tag> tags, Type type) {
        this.type = type;
        this.name = name;
        this.tagKeys = stream(tags.spliterator(), false).map(Tag::getKey).collect(toList());
    }

    public Child child(Iterable<Tag> tags, Iterable<Measurement> measurements) {
        Child child = new Child(stream(tags.spliterator(), false).map(Tag::getValue).collect(toList()), measurements);
        children.add(child);
        return child;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return Collections.singletonList(new MetricFamilySamples(name, type, " ", children.stream()
                .flatMap(Child::collect).collect(toList())));
    }

    class Child implements CustomCollectorChild {
        private final List<String> tagValues;
        private final Iterable<Measurement> measurements;

        Child(List<String> tagValues, Iterable<Measurement> measurements) {
            this.tagValues = tagValues;
            this.measurements = measurements;
        }

        @Override
        public Stream<Collector.MetricFamilySamples.Sample> collect() {
            return stream(measurements.spliterator(), false)
                    .map(m -> new MetricFamilySamples.Sample(name, tagKeys, tagValues, m.getValue()));
        }
    }
}
