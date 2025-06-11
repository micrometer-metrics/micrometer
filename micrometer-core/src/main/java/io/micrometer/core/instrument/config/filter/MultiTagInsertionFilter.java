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
package io.micrometer.core.instrument.config.filter;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * A filter whose job is to inject provided tags in all mapped identifiers, skipping the
 * injection of a particular tag if identifier already has a tag under such a key. In
 * other words, it injects all the provided tags, and in case of conflict it prefers the
 * tag from the identifier rather than provided tags.
 *
 * @see NoOpFilter Skip the processing completely if there are no tags to inject.
 * @since 1.15
 */
public class MultiTagInsertionFilter implements MeterFilter {

    /**
     * Storing injected values directly as Tags saves potential sort processing on every
     * call.
     */
    private final Tags injection;

    MultiTagInsertionFilter(@NonNull Tags injection) {
        this.injection = injection;
    }

    @NonNull
    @Override
    public Meter.Id map(Meter.Id id) {
        Iterable<Tag> original = id.getTagsAsIterable();
        Tags replacement = original == Tags.empty() ? injection : Tags.concat(injection, original);
        return id.replaceTags(replacement);
    }

    public static MeterFilter of(@NonNull Tags injections) {
        return new MultiTagInsertionFilter(injections);
    }

    public static MeterFilter of(@NonNull Tag... injections) {
        return of(Tags.of(injections));
    }

    public static MeterFilter of(@NonNull Iterable<Tag> injections) {
        return of(Tags.of(injections));
    }

}
