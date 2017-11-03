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
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.histogram.HistogramConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * As requests are made of a {@link MeterRegistry} to create new metrics, allow for filtering out
 * the metric altogether, transforming its ID (name or tags) in some way, and transforming its
 * configuration.
 *
 * All new metrics should pass through each {@link MeterFilter} in the order in which they were added.
 *
 * @author Jon Schneider
 */
@Incubating(since = "1.0.0-rc.3")
public interface MeterFilter {
    /**
     * @param id Id with {@link MeterFilter#map} transformations applied.
     * @return After all transformations, should a real meter be registered for this id, or should it be no-op'd.
     */
    default MeterFilterReply accept(Meter.Id id) {
        return MeterFilterReply.NEUTRAL;
    }

    /**
     * @return Transformations to any part of the id.
     */
    default Meter.Id map(Meter.Id id) {
        return id;
    }

    /**
     * This is only called when filtering new timers and distribution summaries (i.e. those meter types
     * that use {@link HistogramConfig}).
     *
     * @param config A histogram configuration guaranteed to be non-null.
     * @return Overrides to any part of the histogram config, when applicable.
     */
    default HistogramConfig configure(HistogramConfig config) {
        return config;
    }

    static MeterFilter commonTags(Iterable<Tag> tags) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> allTags = new ArrayList<>();
                id.getTags().forEach(allTags::add);
                tags.forEach(allTags::add);
                return new Meter.Id(id.getName(), allTags, id.getBaseUnit(), id.getDescription());
            }
        };
    }

    static MeterFilter ignoreTags(String... tagKeys) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> tags = stream(id.getTags().spliterator(), false)
                    .filter(t -> {
                        for (String tagKey : tagKeys) {
                            if (t.getKey().equals(tagKey))
                                return false;
                        }
                        return true;
                    }).collect(Collectors.toList());

                return new Meter.Id(id.getName(), tags, id.getBaseUnit(), id.getDescription());
            }
        };
    }

    /**
     * @author Clint Checketts

     * @param tagKey The tag key for which replacements should be made
     * @param replacement The value to replace with
     * @param exceptions All a matching tag with this value to retain its original value
     */
    static MeterFilter replaceTagValues(String tagKey, Function<String, String> replacement, String... exceptions) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> tags = stream(id.getTags().spliterator(), false)
                    .map(t -> {
                        if(!t.getKey().equals(tagKey))
                            return t;
                        for (String exception : exceptions) {
                            if(t.getValue().equals(exception))
                                return t;
                        }
                        return Tag.of(tagKey, replacement.apply(t.getValue()));
                    })
                    .collect(Collectors.toList());

                return new Meter.Id(id.getName(), tags, id.getBaseUnit(), id.getDescription());
            }
        };
    }

    static MeterFilter accept(Predicate<Meter.Id> iff) {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return iff.test(id) ? MeterFilterReply.ACCEPT : MeterFilterReply.NEUTRAL;
            }
        };
    }

    static MeterFilter deny(Predicate<Meter.Id> iff) {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return iff.test(id) ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        };
    }

    static MeterFilter denyNameStartsWith(String prefix) {
        return deny(id -> id.getName().startsWith(prefix));
    }
}