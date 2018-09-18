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
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * A named and dimensioned producer of one or more measurements.
 *
 * @author Jon Schneider
 */
public interface Meter extends AutoCloseable {
    static Builder builder(String name, Type type, Iterable<Measurement> measurements) {
        return new Builder(name, type, measurements);
    }

    /**
     * @return A unique combination of name and tags
     */
    Id getId();

    /**
     * Get a set of measurements. Should always return the same number of measurements and in
     * the same order, regardless of the level of activity or the lack thereof.
     *
     * @return The set of measurements that represents the instantaneous value of this meter.
     */
    Iterable<Measurement> measure();

    /**
     * Custom meters may emit metrics like one of these types without implementing
     * the corresponding interface. For example, a heisen-counter like structure
     * will emit the same metric as a {@link Counter} but does not have the same
     * increment-driven API.
     */
    enum Type {
        COUNTER,
        GAUGE,
        LONG_TASK_TIMER,
        TIMER,
        DISTRIBUTION_SUMMARY,
        OTHER;

        /**
         * This method contract will change in minor releases if ever a new {@link Meter} type is created.
         * In this case only, this is considered a feature. By using this method, you are declaring that
         * you want to be sure to handle all types of meters. A breaking API change during the introduction of
         * a new {@link Meter} indicates that there is a new meter type for you to consider and the compiler will
         * effectively require you to consider it.
         */
        public static <T> T match(Meter meter,
                                  Function<Gauge, T> visitGauge,
                                  Function<Counter, T> visitCounter,
                                  Function<Timer, T> visitTimer,
                                  Function<DistributionSummary, T> visitSummary,
                                  Function<LongTaskTimer, T> visitLongTaskTimer,
                                  Function<TimeGauge, T> visitTimeGauge,
                                  Function<FunctionCounter, T> visitFunctionCounter,
                                  Function<FunctionTimer, T> visitFunctionTimer,
                                  Function<Meter, T> visitMeter) {
            if (meter instanceof Counter) {
                return visitCounter.apply((Counter) meter);
            } else if (meter instanceof Timer) {
                return visitTimer.apply((Timer) meter);
            } else if (meter instanceof DistributionSummary) {
                return visitSummary.apply((DistributionSummary) meter);
            } else if (meter instanceof TimeGauge) {
                return visitTimeGauge.apply((TimeGauge) meter);
            } else if (meter instanceof Gauge) {
                return visitGauge.apply((Gauge) meter);
            } else if (meter instanceof FunctionTimer) {
                return visitFunctionTimer.apply((FunctionTimer) meter);
            } else if (meter instanceof FunctionCounter) {
                return visitFunctionCounter.apply((FunctionCounter) meter);
            } else if (meter instanceof LongTaskTimer) {
                return visitLongTaskTimer.apply((LongTaskTimer) meter);
            } else {
                return visitMeter.apply(meter);
            }
        }
    }

    /**
     * A meter is uniquely identified by its combination of name and tags.
     */
    class Id {
        private final String name;
        private final List<Tag> tags;
        private final Type type;
        private final boolean synthetic;

        @Nullable
        private final String description;

        @Nullable
        private final String baseUnit;

        @Incubating(since = "1.0.6")
        public Id(String name, Iterable<Tag> tags, @Nullable String baseUnit, @Nullable String description, Type type,
                  boolean synthetic) {
            this.name = name;

            this.tags = Collections.unmodifiableList(stream(tags.spliterator(), false)
                    .sorted(Comparator.comparing(Tag::getKey))
                    .distinct()
                    .collect(Collectors.toList()));

            this.baseUnit = baseUnit;
            this.description = description;
            this.type = type;
            this.synthetic = synthetic;
        }

        public Id(String name, Iterable<Tag> tags, @Nullable String baseUnit, @Nullable String description, Type type) {
            this(name, tags, baseUnit, description, type, false);
        }

        /**
         * Generate a new id with a different name.
         *
         * @param newName The new name.
         * @return A new id with the provided name. The source id remains unchanged.
         */
        public Id withName(String newName) {
            return new Id(newName, tags, baseUnit, description, type);
        }

        /**
         * Generate a new id with an additional tag. If the key of the provided tag already exists, this overwrites
         * the tag value.
         *
         * @param tag The tag to add.
         * @return A new id with the provided tag. The source id remains unchanged.
         */
        public Id withTag(Tag tag) {
            return new Id(name, Tags.concat(tags, Collections.singletonList(tag)), baseUnit, description, type);
        }

        /**
         * Generate a new id with an additional tag with a tag key of "statistic". If the "statistic" tag already exists,
         * this overwrites the tag value.
         *
         * @param statistic The statistic tag to add.
         * @return A new id with the provided tag. The source id remains unchanged.
         */
        public Id withTag(Statistic statistic) {
            return withTag(Tag.of("statistic", statistic.getTagValueRepresentation()));
        }

        /**
         * Generate a new id with a different base unit.
         *
         * @param newBaseUnit The base unit of the new id.
         * @return A new id with the provided base unit.
         */
        public Id withBaseUnit(@Nullable String newBaseUnit) {
            return new Id(name, tags, newBaseUnit, description, type);
        }

        /**
         * @return The name of this meter.
         */
        public String getName() {
            return name;
        }

        /**
         * @return A set of dimensions that allows you to break down the name.
         */
        public List<Tag> getTags() {
            return tags;
        }

        /**
         * @param key The tag key to attempt to match.
         * @return A matching tag value, or {@code null} if no tag with the provided key exists on this id.
         */
        @Nullable
        public String getTag(String key) {
            for (Tag tag : tags) {
                if (tag.getKey().equals(key))
                    return tag.getValue();
            }
            return null;
        }

        /**
         * @return The base unit of measurement for this meter.
         */
        @Nullable
        public String getBaseUnit() {
            return baseUnit;
        }

        /**
         * @param namingConvention The naming convention used to normalize the id's name.
         * @return A name that has been stylized to a particular monitoring system's expectations.
         */
        public String getConventionName(NamingConvention namingConvention) {
            return namingConvention.name(name, type, baseUnit);
        }

        /**
         * Tags that are sorted by key and formatted
         *
         * @param namingConvention The naming convention used to normalize the id's name.
         * @return A list of tags that have been stylized to a particular monitoring system's expectations.
         */
        public List<Tag> getConventionTags(NamingConvention namingConvention) {
            return tags.stream()
                    .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                    .collect(Collectors.toList());
        }

        /**
         * @return A description of the meter's purpose. This description text is published to monitoring systems
         * that support description text.
         */
        @Nullable
        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "MeterId{" +
                    "name='" + name + '\'' +
                    ", tags=" + tags +
                    '}';
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Meter.Id meterId = (Meter.Id) o;
            return Objects.equals(name, meterId.name) && Objects.equals(tags, meterId.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags);
        }

        /**
         * The type is used by different registry implementations to structure the exposition
         * of metrics to different backends.
         *
         * @return The meter's type.
         */
        public Type getType() {
            return type;
        }

        /**
         * For internal use. Indicates that this Id is tied to a meter that is a derivative of another metric.
         * For example, percentiles and histogram gauges generated by {@link HistogramGauges} are derivatives
         * of a {@link Timer} or {@link DistributionSummary}.
         * <p>
         * This method may be removed in future minor or major releases if we find a way to mark derivatives in a
         * private way that does not have other API compatibility consequences.
         *
         * @return Whether this gauge is a synthetic derivative or not.
         */
        @Incubating(since = "1.0.6")
        public boolean isSynthetic() {
            return synthetic;
        }
    }

    /**
     * Fluent builder for custom meters.
     */
    class Builder {
        private final String name;
        private final Type type;
        private final Iterable<Measurement> measurements;
        private final List<Tag> tags = new ArrayList<>();

        @Nullable
        private String description;

        @Nullable
        private String baseUnit;

        private Builder(String name, Type type, Iterable<Measurement> measurements) {
            this.name = name;
            this.type = type;
            this.measurements = measurements;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The custom meter builder with added tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual meter.
         * @return The custom meter builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The custom meter builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            tags.add(Tag.of(key, value));
            return this;
        }

        /**
         * @param description Description text of the eventual meter.
         * @return The custom meter builder with added description.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * @param unit Base unit of the eventual meter.
         * @return The custom meter builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        /**
         * Add the meter to a single registry, or return an existing meter in that registry. The returned
         * meter will be unique for each registry, but each registry is guaranteed to only create one meter
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the custom meter to, if it doesn't already exist.
         * @return A new or existing custom meter.
         */
        public Meter register(MeterRegistry registry) {
            return registry.register(new Meter.Id(name, tags, baseUnit, description, type), type, measurements);
        }
    }

    @Override
    default void close() {
    }
}
