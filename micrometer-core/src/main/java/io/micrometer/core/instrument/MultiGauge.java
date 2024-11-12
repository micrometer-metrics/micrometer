/*
 * Copyright 2018 VMware, Inc.
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

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * @author Jon Schneider
 * @since 1.1.0
 */
@Incubating(since = "1.1.0")
public class MultiGauge {

    private final MeterRegistry registry;

    private final Meter.Id commonId;

    private final AtomicReference<Set<Meter.Id>> registeredRows = new AtomicReference<>(emptySet());

    private MultiGauge(MeterRegistry registry, Meter.Id commonId) {
        this.registry = registry;
        this.commonId = commonId;
    }

    /**
     * @param name The gauge's name.
     * @return A new gauge builder.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public void register(Iterable<? extends Row<?>> rows) {
        register(rows, false);
    }

    @SuppressWarnings("unchecked")
    public void register(Iterable<? extends Row<?>> rows, boolean overwrite) {
        registeredRows.getAndUpdate(oldRows -> {
            // for some reason the compiler needs type assistance by creating this
            // intermediate variable.
            Stream<Meter.Id> idStream = StreamSupport.stream(rows.spliterator(), false).map(row -> {
                Row r = row;
                Meter.Id rowId = commonId.withTags(r.uniqueTags);
                boolean previouslyDefined = oldRows.contains(rowId);

                if (overwrite && previouslyDefined) {
                    registry.removeByPreFilterId(rowId);
                }

                if (overwrite || !previouslyDefined) {
                    registry.gauge(rowId, r.obj, new StrongReferenceGaugeFunction<>(r.obj, r.valueFunction));
                }

                return rowId;
            });

            Set<Meter.Id> newRows = idStream.collect(toSet());

            for (Meter.Id oldRow : oldRows) {
                if (!newRows.contains(oldRow))
                    registry.removeByPreFilterId(oldRow);
            }

            return newRows;
        });
    }

    public static class Row<T> {

        private final Tags uniqueTags;

        private final T obj;

        private final ToDoubleFunction<T> valueFunction;

        private Row(Tags uniqueTags, T obj, ToDoubleFunction<T> valueFunction) {
            this.uniqueTags = uniqueTags;
            this.obj = obj;
            this.valueFunction = valueFunction;
        }

        public static <T> Row<T> of(Tags uniqueTags, T obj, ToDoubleFunction<T> valueFunction) {
            return new Row<>(uniqueTags, obj, valueFunction);
        }

        public static Row<Number> of(Tags uniqueTags, Number number) {
            return new Row<>(uniqueTags, number, Number::doubleValue);
        }

        public static <T extends Number> Row<Supplier<T>> of(Tags uniqueTags, Supplier<T> valueFunction) {
            return new Row<>(uniqueTags, valueFunction, f -> {
                Number value = valueFunction.get();
                return value == null ? Double.NaN : value.doubleValue();
            });
        }

    }

    /**
     * Fluent builder for multi-gauges.
     */
    public static class Builder {

        private final String name;

        private Tags tags = Tags.empty();

        @Nullable
        private String description;

        @Nullable
        private String baseUnit;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
         * @return The gauge builder with added tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual gauge.
         * @return The gauge builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key The tag key.
         * @param value The tag value.
         * @return The gauge builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual gauge.
         * @return The gauge builder with added description.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * @param unit Base unit of the eventual gauge.
         * @return The gauge builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        public MultiGauge register(MeterRegistry registry) {
            return new MultiGauge(registry, new Meter.Id(name, tags, baseUnit, description, Meter.Type.GAUGE, null));
        }

    }

}
