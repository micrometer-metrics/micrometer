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
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Tag;
import io.prometheus.client.Collector;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface CustomCollectorChild {
    Stream<Collector.MetricFamilySamples.Sample> collect();

    default Iterable<Measurement> measure() {
        return collect().map(sample -> {
            List<Tag> tags = IntStream.range(0, sample.labelNames.size())
                    .mapToObj(i -> Tag.of(sample.labelNames.get(i), sample.labelValues.get(i)))
                    .collect(Collectors.toList());
            return new Measurement(sample.name, tags, sample.value);
        }).collect(Collectors.toList());
    }
}
