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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.client.Collector;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider
 */
class MicrometerCollector extends Collector {
    interface Child {
        Stream<Family> samples(String conventionName, List<String> tagKeys);
    }

    static class Family {
        final Type type;
        final String conventionName;
        final Stream<MetricFamilySamples.Sample> samples;

        Family(Type type, String conventionName, Stream<MetricFamilySamples.Sample> samples) {
            this.type = type;
            this.conventionName = conventionName;
            this.samples = samples;
        }

        String getConventionName() {
            return conventionName;
        }
    }

    private final Meter.Id id;
    private final List<Child> children = new CopyOnWriteArrayList<>();
    private final String conventionName;
    private final List<String> tagKeys;
    private final PrometheusConfig config;

    public MicrometerCollector(Meter.Id id, NamingConvention convention, PrometheusConfig config) {
        this.id = id;
        this.conventionName = id.getConventionName(convention);
        this.tagKeys = id.getConventionTags(convention).stream().map(Tag::getKey).collect(toList());
        this.config = config;
    }

    public void add(Child child) {
        children.add(child);
    }

    public List<String> getTagKeys() {
        return tagKeys;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        final String help = config.descriptions() ? Optional.ofNullable(id.getDescription()).orElse(" ") : " ";

        return children.stream()
            .flatMap(child -> child.samples(conventionName, tagKeys))
            .collect(Collectors.groupingBy(
                Family::getConventionName,
                Collectors.reducing((a, b) -> new Family(a.type, a.conventionName, Stream.concat(a.samples, b.samples)))))
            .values().stream()
            .map(Optional::get)
            .map(family -> new MetricFamilySamples(family.conventionName, family.type, help, family.samples.collect(toList())))
            .collect(toList());
    }
}
