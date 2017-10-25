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
import io.micrometer.core.instrument.NamingConvention;
import io.micrometer.core.instrument.Tag;
import io.prometheus.client.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider
 */
class MicrometerCollector extends Collector {
    interface Child {
        Stream<MetricFamilySamples.Sample> samples(String conventionName, List<String> tagKeys);
    }

    private final Meter.Id id;
    private final List<Child> children = new ArrayList<>();
    private Type type;
    private final String conventionName;
    private final List<String> tagKeys;
    private final PrometheusConfig config;

    public MicrometerCollector(Meter.Id id, Type type, NamingConvention convention, PrometheusConfig config) {
        this.id = id;
        this.type = type;
        this.conventionName = id.getConventionName(convention);
        this.tagKeys = id.getConventionTags(convention).stream().map(Tag::getKey).collect(toList());
        this.config = config;
    }

    public void add(Child child) {
        children.add(child);
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        String help = config.descriptions() ? id.getDescription() : " ";
        if(help == null)
            help = " ";

        return Collections.singletonList(new MetricFamilySamples(conventionName, type, help,
            children.stream().flatMap(child -> child.samples(conventionName, tagKeys)).collect(toList())));
    }
}
