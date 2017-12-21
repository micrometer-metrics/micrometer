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

import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.Assert;
import io.micrometer.core.lang.Nullable;

import java.beans.Introspector;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * A counter, gauge, timer, or distribution summary that results collects one or more metrics.
 */
public interface Meter {
    /**
     * A unique combination of name and tags
     */
    Id getId();

    /**
     * Get a set of measurements. Should always return
     * the same number of measurements and in the same order, regardless of the
     * level of activity or the lack thereof.
     */
    Iterable<Measurement> measure();

    default Type type() {
        return Type.Other;
    }

    /**
     * Custom meters may emit metrics like one of these types without implementing
     * the corresponding interface. For example, a heisen-counter like structure
     * will emit the same metric as a {@link Counter} but does not have the same
     * increment-driven API.
     */
    enum Type {
        Counter,
        Gauge,
        LongTaskTimer,
        Timer,
        DistributionSummary,
        Other
    }

    class Id {
        private final String name;
        private final List<Tag> tags;
        private @Nullable String baseUnit;
        private final @Nullable String description;
        private Type type;

        public Id(String name, Iterable<Tag> tags, @Nullable String baseUnit, @Nullable String description, Type type) {
            Assert.notNull(name, "name");
            Assert.notNull(tags, "tags");
            Assert.notNull(type, "type");
            this.name = name;

            this.tags = Collections.unmodifiableList(stream(tags.spliterator(), false)
                .sorted(Comparator.comparing(Tag::getKey))
                .distinct()
                .collect(Collectors.toList()));

            this.baseUnit = baseUnit;
            this.description = description;

            this.type = type;
        }

        public Id withTag(Tag tag) {
            Assert.notNull(tag, "tag");
            return new Id(name, Tags.concat(tags, Collections.singletonList(tag)), baseUnit, description, type);
        }

        public Id withTag(Statistic statistic) {
            Assert.notNull(statistic, "statistic");
            return withTag(Tag.of("statistic", Introspector.decapitalize(statistic.toString())));
        }

        public Id withBaseUnit(@Nullable String newBaseUnit) {
            return new Id(name, tags, newBaseUnit, description, type);
        }

        public String getName() {
            return name;
        }

        public Iterable<Tag> getTags() {
            return tags;
        }

        public String getBaseUnit() {
            return baseUnit;
        }

        public String getConventionName(NamingConvention namingConvention) {
            return namingConvention.name(name, type, baseUnit);
        }

        public String getDescription() {
            return description;
        }

        /**
         * Tags that are sorted by key and formatted
         */
        public List<Tag> getConventionTags(NamingConvention namingConvention) {
            Assert.notNull(namingConvention, "namingConvention");
            return tags.stream()
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .collect(Collectors.toList());
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

        public Type getType() {
            return type;
        }
    }

    static Builder builder(String name, Type type, Iterable<Measurement> measurements) {
        return new Builder(name, type, measurements);
    }

    /**
     * Builder for custom meter types
     */
    class Builder {
        private final String name;
        private final Type type;
        private final Iterable<Measurement> measurements;
        private final List<Tag> tags = new ArrayList<>();
        private @Nullable String description;
        private @Nullable String baseUnit;

        private Builder(String name, Type type, Iterable<Measurement> measurements) {
            Assert.notNull(name, "name");
            Assert.notNull(type, "type");
            Assert.notNull(measurements, "measurements");
            this.name = name;
            this.type = type;
            this.measurements = measurements;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        public Meter register(MeterRegistry registry) {
            Assert.notNull(registry, "registry");
            return registry.register(new Meter.Id(name, tags, baseUnit, description, type), type, measurements);
        }
    }
}
