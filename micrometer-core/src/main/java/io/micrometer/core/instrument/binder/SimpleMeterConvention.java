/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.binder;

import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Basic implementation of a {@link MeterConvention}.
 *
 * @param <C> context type from which tags can be derived
 * @since 1.16.0
 */
public class SimpleMeterConvention<C extends @Nullable Object> implements MeterConvention<C> {

    private final String name;

    private final @Nullable Tags tags;

    private final @Nullable Function<C, Tags> tagsFunction;

    /**
     * Create a convention with a name and no tags.
     * @param name meter name
     */
    public SimpleMeterConvention(String name) {
        this(name, Tags.empty());
    }

    /**
     * Create a convention with a name and fixed tags.
     * @param name meter name
     * @param tags tags associated with the meter
     */
    public SimpleMeterConvention(String name, Tags tags) {
        this.name = name;
        this.tags = tags;
        this.tagsFunction = null;
    }

    /**
     * Create a convention with a name and tags derived from a function.
     * @param name meter name
     * @param tagsFunction derive tags from the context with this function
     */
    public SimpleMeterConvention(String name, Function<C, Tags> tagsFunction) {
        this.name = name;
        this.tags = null;
        this.tagsFunction = Objects.requireNonNull(tagsFunction);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Tags getTags(C context) {
        return tags == null ? Objects.requireNonNull(tagsFunction).apply(context) : tags;
    }

}
