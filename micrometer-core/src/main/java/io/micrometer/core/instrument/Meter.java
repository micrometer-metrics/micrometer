/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramGauges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Collections.singletonList;

/**
 * A named and dimensioned producer of one or more measurements.
 *
 * @author Jon Schneider
 * @author Jonatan Ivanov
 */
public interface Meter {

    static Builder builder(String name, Type type, Iterable<Measurement> measurements) {
        return new Builder(name, type, measurements);
    }

    /**
     * @return A unique combination of name and tags
     */
    Id getId();

    /**
     * Get a set of measurements. Should always return the same number of measurements and
     * in the same order, regardless of the level of activity or the lack thereof.
     * @return The set of measurements that represents the instantaneous value of this
     * meter.
     */
    Iterable<Measurement> measure();

    /**
     * Custom meters may emit metrics like one of these types without implementing the
     * corresponding interface. For example, a heisen-counter like structure will emit the
     * same metric as a {@link Counter} but does not have the same increment-driven API.
     */
    enum Type {

        COUNTER, GAUGE, LONG_TASK_TIMER, TIMER, DISTRIBUTION_SUMMARY, OTHER;

    }

    /**
     * Match a {@link Meter} by type with series of dedicated functions for specific
     * {@link Meter}s and return a result from the matched function.
     * <p>
     * NOTE: This method contract will change in minor releases if ever a new
     * {@link Meter} type is created. In this case only, this is considered a feature. By
     * using this method, you are declaring that you want to be sure to handle all types
     * of meters. A breaking API change during the introduction of a new {@link Meter}
     * indicates that there is a new meter type for you to consider and the compiler will
     * effectively require you to consider it.
     * @param visitGauge function to apply for {@link Gauge}
     * @param visitCounter function to apply for {@link Counter}
     * @param visitTimer function to apply for {@link Timer}
     * @param visitSummary function to apply for {@link DistributionSummary}
     * @param visitLongTaskTimer function to apply for {@link LongTaskTimer}
     * @param visitTimeGauge function to apply for {@link TimeGauge}
     * @param visitFunctionCounter function to apply for {@link FunctionCounter}
     * @param visitFunctionTimer function to apply for {@link FunctionTimer}
     * @param visitMeter function to apply as a fallback
     * @param <T> return type of function to apply
     * @return return value from the applied function
     * @since 1.1.0
     */
    default <T> T match(Function<Gauge, T> visitGauge, Function<Counter, T> visitCounter, Function<Timer, T> visitTimer,
            Function<DistributionSummary, T> visitSummary, Function<LongTaskTimer, T> visitLongTaskTimer,
            Function<TimeGauge, T> visitTimeGauge, Function<FunctionCounter, T> visitFunctionCounter,
            Function<FunctionTimer, T> visitFunctionTimer, Function<Meter, T> visitMeter) {
        if (this instanceof TimeGauge) {
            return visitTimeGauge.apply((TimeGauge) this);
        }
        else if (this instanceof Gauge) {
            return visitGauge.apply((Gauge) this);
        }
        else if (this instanceof Counter) {
            return visitCounter.apply((Counter) this);
        }
        else if (this instanceof Timer) {
            return visitTimer.apply((Timer) this);
        }
        else if (this instanceof DistributionSummary) {
            return visitSummary.apply((DistributionSummary) this);
        }
        else if (this instanceof LongTaskTimer) {
            return visitLongTaskTimer.apply((LongTaskTimer) this);
        }
        else if (this instanceof FunctionCounter) {
            return visitFunctionCounter.apply((FunctionCounter) this);
        }
        else if (this instanceof FunctionTimer) {
            return visitFunctionTimer.apply((FunctionTimer) this);
        }
        else {
            return visitMeter.apply(this);
        }
    }

    /**
     * Match a {@link Meter} with a series of dedicated functions for specific
     * {@link Meter}s and call the matching consumer.
     * <p>
     * NOTE: This method contract will change in minor releases if ever a new
     * {@link Meter} type is created. In this case only, this is considered a feature. By
     * using this method, you are declaring that you want to be sure to handle all types
     * of meters. A breaking API change during the introduction of a new {@link Meter}
     * indicates that there is a new meter type for you to consider and the compiler will
     * effectively require you to consider it.
     * @param visitGauge function to apply for {@link Gauge}
     * @param visitCounter function to apply for {@link Counter}
     * @param visitTimer function to apply for {@link Timer}
     * @param visitSummary function to apply for {@link DistributionSummary}
     * @param visitLongTaskTimer function to apply for {@link LongTaskTimer}
     * @param visitTimeGauge function to apply for {@link TimeGauge}
     * @param visitFunctionCounter function to apply for {@link FunctionCounter}
     * @param visitFunctionTimer function to apply for {@link FunctionTimer}
     * @param visitMeter function to apply as a fallback
     * @since 1.1.0
     */
    default void use(Consumer<Gauge> visitGauge, Consumer<Counter> visitCounter, Consumer<Timer> visitTimer,
            Consumer<DistributionSummary> visitSummary, Consumer<LongTaskTimer> visitLongTaskTimer,
            Consumer<TimeGauge> visitTimeGauge, Consumer<FunctionCounter> visitFunctionCounter,
            Consumer<FunctionTimer> visitFunctionTimer, Consumer<Meter> visitMeter) {
        if (this instanceof TimeGauge) {
            visitTimeGauge.accept((TimeGauge) this);
        }
        else if (this instanceof Gauge) {
            visitGauge.accept((Gauge) this);
        }
        else if (this instanceof Counter) {
            visitCounter.accept((Counter) this);
        }
        else if (this instanceof Timer) {
            visitTimer.accept((Timer) this);
        }
        else if (this instanceof DistributionSummary) {
            visitSummary.accept((DistributionSummary) this);
        }
        else if (this instanceof LongTaskTimer) {
            visitLongTaskTimer.accept((LongTaskTimer) this);
        }
        else if (this instanceof FunctionCounter) {
            visitFunctionCounter.accept((FunctionCounter) this);
        }
        else if (this instanceof FunctionTimer) {
            visitFunctionTimer.accept((FunctionTimer) this);
        }
        else {
            visitMeter.accept(this);
        }
    }

    /**
     * A meter is uniquely identified by its combination of name and tags.
     */
    class Id {

        private final String name;

        private final Tags tags;

        private final Type type;

        @Nullable
        private final Meter.Id syntheticAssociation;

        @Nullable
        private final String description;

        @Nullable
        private final String baseUnit;

        @Incubating(since = "1.1.0")
        Id(String name, Tags tags, @Nullable String baseUnit, @Nullable String description, Type type,
                @Nullable Meter.Id syntheticAssociation) {
            this.name = name;
            this.tags = tags;
            this.baseUnit = baseUnit;
            this.description = description;
            this.type = type;
            this.syntheticAssociation = syntheticAssociation;
        }

        public Id(String name, Tags tags, @Nullable String baseUnit, @Nullable String description, Type type) {
            this(name, tags, baseUnit, description, type, null);
        }

        /**
         * Generate a new id with a different name.
         * @param newName The new name.
         * @return A new id with the provided name. The source id remains unchanged.
         */
        public Id withName(String newName) {
            return new Id(newName, tags, baseUnit, description, type);
        }

        /**
         * Generate a new id with an additional tag. If the key of the provided tag
         * already exists, this overwrites the tag value.
         * @param tag The tag to add.
         * @return A new id with the provided tag added. The source id remains unchanged.
         */
        public Id withTag(Tag tag) {
            return withTags(singletonList(tag));
        }

        /**
         * Generate a new id with additional tags. If the key of the provided tag already
         * exists, this overwrites the tag value.
         * @param tags The tags to add.
         * @return A new id with the provided tags added. The source id remains unchanged.
         * @since 1.1.0
         */
        public Id withTags(Iterable<Tag> tags) {
            return new Id(name, Tags.concat(getTags(), tags), baseUnit, description, type);
        }

        /**
         * Generate a new id replacing all tags with new ones.
         * @param tags The tags to add.
         * @return A new id with the only the provided tags. The source id remains
         * unchanged.
         * @since 1.1.0
         */
        public Id replaceTags(Iterable<Tag> tags) {
            return new Id(name, Tags.of(tags), baseUnit, description, type);
        }

        /**
         * Generate a new id with an additional tag with a tag key of "statistic". If the
         * "statistic" tag already exists, this overwrites the tag value.
         * @param statistic The statistic tag to add.
         * @return A new id with the provided tag. The source id remains unchanged.
         */
        public Id withTag(Statistic statistic) {
            return withTag(Tag.of("statistic", statistic.getTagValueRepresentation()));
        }

        /**
         * Generate a new id with a different base unit.
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
            List<Tag> tags = new ArrayList<>();
            this.tags.forEach(tags::add);
            return Collections.unmodifiableList(tags);
        }

        public Iterable<Tag> getTagsAsIterable() {
            return tags;
        }

        /**
         * @param key The tag key to attempt to match.
         * @return A matching tag value, or {@code null} if no tag with the provided key
         * exists on this id.
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
         * @return A name that has been stylized to a particular monitoring system's
         * expectations.
         */
        public String getConventionName(NamingConvention namingConvention) {
            return namingConvention.name(name, type, baseUnit);
        }

        /**
         * Tags that are sorted by key and formatted
         * @param namingConvention The naming convention used to normalize the id's name.
         * @return A list of tags that have been stylized to a particular monitoring
         * system's expectations.
         */
        public List<Tag> getConventionTags(NamingConvention namingConvention) {
            return StreamSupport.stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .collect(Collectors.toList());
        }

        /**
         * @return A description of the meter's purpose. This description text is
         * published to monitoring systems that support description text.
         */
        @Nullable
        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "MeterId{" + "name='" + name + '\'' + ", tags=" + tags + '}';
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Meter.Id meterId = (Meter.Id) o;
            return name.equals(meterId.name) && tags.equals(meterId.tags);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + tags.hashCode();
            return result;
        }

        /**
         * The type is used by different registry implementations to structure the
         * exposition of metrics to different backends.
         * @return The meter's type.
         */
        public Type getType() {
            return type;
        }

        /**
         * For internal use. Indicates that this Id is tied to a meter that is a
         * derivative of another metric. For example, percentiles and histogram gauges
         * generated by {@link HistogramGauges} are derivatives of a {@link Timer} or
         * {@link DistributionSummary}.
         * <p>
         * This method may be removed in future minor or major releases if we find a way
         * to mark derivatives in a private way that does not have other API compatibility
         * consequences.
         * @return The meter id of a meter for which this metric is a synthetic
         * derivative.
         */
        @Incubating(since = "1.1.0")
        @Nullable
        public Meter.Id syntheticAssociation() {
            return syntheticAssociation;
        }

    }

    /**
     * Fluent builder for custom meters.
     */
    class Builder {

        private final String name;

        private final Type type;

        private final Iterable<Measurement> measurements;

        private Tags tags = Tags.empty();

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
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
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
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key The tag key.
         * @param value The tag value.
         * @return The custom meter builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
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
         * Add the meter to a single registry, or return an existing meter in that
         * registry. The returned meter will be unique for each registry, but each
         * registry is guaranteed to only create one meter for the same combination of
         * name and tags.
         * @param registry A registry to add the custom meter to, if it doesn't already
         * exist.
         * @return A new or existing custom meter.
         */
        public Meter register(MeterRegistry registry) {
            return registry.register(new Meter.Id(name, tags, baseUnit, description, type), type, measurements);
        }

    }

    /**
     * Convenience interface to create new meters from tags based on a common
     * "template"/builder. See usage in Meter implementations, e.g.: {@code Timer},
     * {@code Counter}
     *
     * @param <T> Meter type
     * @since 1.12.0
     */
    interface MeterProvider<T extends Meter> {

        /**
         * Registers (creates a new or gets an existing one if already exists) Meters
         * using the provided tags.
         * @param tags Tags to attach to the Meter about to be registered
         * @return A new or existing Meter
         */
        T withTags(Iterable<? extends Tag> tags);

        /**
         * Registers (creates a new or gets an existing one if already exists) Meters
         * using the provided tags.
         * @param tags Tags to attach to the Meter about to be registered
         * @return A new or existing Meter
         */
        default T withTags(String... tags) {
            return withTags(Tags.of(tags));
        }

        /**
         * Registers (creates a new or gets an existing one if already exists) Meters
         * using the provided tags.
         * @param key the tag key to add
         * @param value the tag value to add
         * @return A new or existing Meter
         */
        default T withTag(String key, String value) {
            return withTags(Tags.of(key, value));
        }

    }

    default void close() {
    }

}
