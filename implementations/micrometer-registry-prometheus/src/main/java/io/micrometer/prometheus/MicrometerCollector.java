/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider
 */
class MicrometerCollector extends Collector {
    private final Meter.Id id;
    private final Map<List<String>, Child> children = new ConcurrentHashMap<>();
    private final String conventionName;
    private final List<String> tagKeys;
    private final PrometheusConfig config;

    public MicrometerCollector(Meter.Id id, NamingConvention convention, PrometheusConfig config) {
        this.id = id;
        this.conventionName = id.getConventionName(convention);
        this.tagKeys = id.getConventionTags(convention).stream().map(Tag::getKey).collect(toList());
        this.config = config;
    }

    public void add(List<String> tagValues, Child child) {
        children.put(tagValues, child);
    }

    public void remove(List<String> tagValues) {
        children.remove(tagValues);
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    public List<String> getTagKeys() {
        return tagKeys;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        final String help = config.descriptions() ? Optional.ofNullable(id.getDescription()).orElse(" ") : " ";

        Map<String, Family> families = new HashMap<>();

        for (Child child : children.values()) {
            child.samples(conventionName, tagKeys).forEach(family -> {
                families.compute(family.getConventionName(), (name, matchingFamily) -> matchingFamily != null ?
                        matchingFamily.addSamples(family.samples) : family);
            });
        }

        return families.values().stream()
                .map(family -> new MetricFamilySamples(family.conventionName, family.type, help, family.samples))
                .collect(toList());
    }

    interface Child {
        Stream<Family> samples(String conventionName, List<String> tagKeys);
    }

    static class Family {
        final Type type;
        final String conventionName;
        final List<MetricFamilySamples.Sample> samples = new ArrayList<>();

        Family(Type type, String conventionName, MetricFamilySamples.Sample... samples) {
            this.type = type;
            this.conventionName = conventionName;
            Collections.addAll(this.samples, samples);
        }

        Family(Type type, String conventionName, Stream<MetricFamilySamples.Sample> samples) {
            this.type = type;
            this.conventionName = conventionName;
            samples.forEach(this.samples::add);
        }

        String getConventionName() {
            return conventionName;
        }

        Family addSamples(Collection<MetricFamilySamples.Sample> samples) {
            this.samples.addAll(samples);
            return this;
        }
    }
}
