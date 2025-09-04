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

public class SimpleMeterConvention<T extends @Nullable Object> implements MeterConvention<T> {

    private final String name;

    private final @Nullable Tags tags;

    private final @Nullable Function<T, Tags> tagsFunction;

    public SimpleMeterConvention(String name) {
        this(name, Tags.empty());
    }

    public SimpleMeterConvention(String name, Tags tags) {
        this.name = name;
        this.tags = tags;
        this.tagsFunction = null;
    }

    public SimpleMeterConvention(String name, Function<T, Tags> tagsFunction) {
        this.name = name;
        this.tags = null;
        this.tagsFunction = tagsFunction;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Tags getTags(T context) {
        return tags == null ? Objects.requireNonNull(tagsFunction).apply(context) : tags;
    }

}
