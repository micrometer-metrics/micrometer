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
package io.micrometer.core.instrument.config;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * As requests are made of a {@link MeterRegistry} to create new metrics, allow for filtering out
 * the metric altogether, transforming its ID (name or tags) in some way, and transforming its
 * configuration.
 * <p>
 * All new metrics should pass through each {@link MeterFilter} in the order in which they were added.
 *
 * @author Jon Schneider
 */
@Incubating(since = "1.0.0-rc.3")
public interface MeterFilter {
    static MeterFilter commonTags(Iterable<Tag> tags) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> allTags = new ArrayList<>();
                id.getTags().forEach(allTags::add);
                tags.forEach(allTags::add);
                return new Meter.Id(id.getName(), allTags, id.getBaseUnit(), id.getDescription(), id.getType());
            }
        };
    }

    static MeterFilter renameTag(String metricPrefix, String fromTagKey, String toTagKey) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!id.getName().startsWith(metricPrefix))
                    return id;

                List<Tag> tags = new ArrayList<>();
                for (Tag tag : id.getTags()) {
                    if (tag.getKey().equals(fromTagKey))
                        tags.add(Tag.of(toTagKey, tag.getValue()));
                    else tags.add(tag);
                }

                return new Meter.Id(id.getName(), tags, id.getBaseUnit(), id.getDescription(), id.getType());
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

                return new Meter.Id(id.getName(), tags, id.getBaseUnit(), id.getDescription(), id.getType());
            }
        };
    }

    /**
     * @param tagKey      The tag key for which replacements should be made
     * @param replacement The value to replace with
     * @param exceptions  All a matching tag with this value to retain its original value
     * @author Clint Checketts
     */
    static MeterFilter replaceTagValues(String tagKey, Function<String, String> replacement, String... exceptions) {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> tags = stream(id.getTags().spliterator(), false)
                    .map(t -> {
                        if (!t.getKey().equals(tagKey))
                            return t;
                        for (String exception : exceptions) {
                            if (t.getValue().equals(exception))
                                return t;
                        }
                        return Tag.of(tagKey, replacement.apply(t.getValue()));
                    })
                    .collect(Collectors.toList());

                return new Meter.Id(id.getName(), tags, id.getBaseUnit(), id.getDescription(), id.getType());
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

    static MeterFilter accept() {
        return MeterFilter.accept(id -> true);
    }

    static MeterFilter deny() {
        return MeterFilter.deny(id -> true);
    }

    /**
     * Useful for cost-control in monitoring systems which charge directly or indirectly by the
     * total number of time series you generate.
     * <p>
     * While this filter doesn't discriminate between your most critical and less useful metrics in
     * deciding what to drop (all the metrics you intend to use should fit below this threshold),
     * it can effectively cap your risk of an accidentally high-cardiality metric costing too much.
     *
     * @param maximumTimeSeries The total number of unique name/tag permutations allowed before filtering kicks in.
     */
    static MeterFilter maximumAllowableMetrics(int maximumTimeSeries) {
        return new MeterFilter() {
            private final Set<Meter.Id> ids = ConcurrentHashMap.newKeySet();

            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (ids.size() > maximumTimeSeries)
                    return MeterFilterReply.DENY;

                ids.add(id);
                return ids.size() > maximumTimeSeries ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        };
    }

    /**
     * Places an upper bound on
     *
     * @param meterName
     * @param tagKey
     * @param maximumTagValues
     * @param onMaxReached
     * @return
     */
    static MeterFilter maximumAllowableTags(String meterName, String tagKey, int maximumTagValues,
                                            MeterFilter onMaxReached) {
        return new MeterFilter() {
            private final Set<String> observedTagValues = new ConcurrentSkipListSet<>();

            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (id.getName().equals(meterName)) {
                    String value = id.getTag(tagKey);
                    if (value != null)
                        observedTagValues.add(value);
                }

                if (observedTagValues.size() > maximumTagValues) {
                    return onMaxReached.accept(id);
                }
                return MeterFilterReply.NEUTRAL;
            }

            @Override
            public HistogramConfig configure(Meter.Id id, HistogramConfig config) {
                if (observedTagValues.size() > maximumTagValues) {
                    return onMaxReached.configure(id, config);
                }
                return config;
            }
        };
    }

    static MeterFilter denyNameStartsWith(String prefix) {
        return deny(id -> id.getName().startsWith(prefix));
    }

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
     * @param id     Id with {@link MeterFilter#map} transformations applied.
     * @param config A histogram configuration guaranteed to be non-null.
     * @return Overrides to any part of the histogram config, when applicable.
     */
    @Nullable
    default HistogramConfig configure(Meter.Id id, HistogramConfig config) {
        return config;
    }
}
