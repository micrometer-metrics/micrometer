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
package io.micrometer.core.instrument;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import static io.micrometer.core.instrument.Tags.zip;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry to iterate
 * over the set of meters instrumenting your application, and then further iterate over each meter's metrics, generally
 * resulting in a time series in the metrics backend for each combination of metrics and dimensions.
 *
 * @author Jon Schneider
 */
public interface MeterRegistryConfigurator {

    /**
     * Append a list of common tags to apply to all metrics reported to the monitoring system.
     * Use {@link Tags#zip(String...) as a simple way to generate tags}
     */
    void commonTags(Iterable<Tag> tags);

    <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags);

    /**
     * @return The set of registered meters.
     */
    Collection<Meter> getMeters();

    Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags);

    Clock getClock();

    static MeterRegistryConfigurator as(MeterRegistry registry) {
        return (MeterRegistryConfigurator)registry;
    }
}
