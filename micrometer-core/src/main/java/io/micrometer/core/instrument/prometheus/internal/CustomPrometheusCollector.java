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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class CustomPrometheusCollector extends Collector {
    private final String name;
    private final Type type;
    private final Collection<Child> children = new ConcurrentLinkedQueue<>();

    public CustomPrometheusCollector(String name, Type type) {
        this.type = type;
        this.name = name;
    }

    public Child child(Supplier<List<Measurement>> measurementSupplier) {
        Child child = new Child(measurementSupplier);
        children.add(child);
        return child;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return Collections.singletonList(new MetricFamilySamples(name, type, " ", children.stream()
                .flatMap(Child::collect).collect(toList())));
    }

    class Child implements CustomCollectorChild {
        private final Supplier<List<Measurement>> measurementSupplier;

        Child(Supplier<List<Measurement>> measurementSupplier) {
            this.measurementSupplier = measurementSupplier;
        }

        @Override
        public Stream<Collector.MetricFamilySamples.Sample> collect() {
            return measurementSupplier.get().stream()
                    .map(m -> {
                        List<String> keys = m.getTags().stream().map(Tag::getKey).collect(toList());
                        List<String> values = m.getTags().stream().map(Tag::getValue).collect(toList());
                        return new MetricFamilySamples.Sample(name, keys, values, m.getValue());
                    });
        }
    }
}
