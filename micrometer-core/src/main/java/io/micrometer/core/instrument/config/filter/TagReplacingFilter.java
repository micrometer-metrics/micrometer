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
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class TagReplacingFilter implements MeterFilter {

    private final BiPredicate<String, String> filter;

    private final BiFunction<String, String, Tag> replacer;

    private final int expectedTagCount;

    TagReplacingFilter(BiPredicate<String, String> filter, BiFunction<String, String, Tag> replacer,
            int expectedTagCount) {
        this.replacer = replacer;
        this.filter = filter;
        this.expectedTagCount = expectedTagCount;
    }

    @NonNull
    @Override
    public Meter.Id map(@NonNull Meter.Id id) {
        Iterator<Tag> iterator = id.getTagsAsIterable().iterator();

        if (!iterator.hasNext()) {
            // fast path avoiding list allocation completely
            return id;
        }

        List<Tag> replacement = new ArrayList<>(expectedTagCount);

        boolean intercepted = false;
        while (iterator.hasNext()) {
            Tag tag = iterator.next();
            String key = tag.getKey();
            String value = tag.getValue();

            if (filter.test(key, value)) {
                replacement.add(replacer.apply(key, value));
                intercepted = true;
            }
            else {
                replacement.add(tag);
            }
        }

        return intercepted ? id.replaceTags(replacement) : id;
    }

    public static MeterFilter of(BiPredicate<String, String> filter, BiFunction<String, String, Tag> replacer,
            int expectedSize) {
        return new TagReplacingFilter(filter, replacer, expectedSize);
    }

    public static MeterFilter of(BiPredicate<String, String> filter, BiFunction<String, String, Tag> replacer) {
        return new TagReplacingFilter(filter, replacer, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

    public static MeterFilter classicValueReplacing(String key, Function<String, String> replacer,
            Collection<String> exceptions, int expectedSize) {
        return of(new ClassicFilter(key, new HashSet<>(exceptions)), new ValueReplacer(replacer), expectedSize);
    }

    public static MeterFilter classicValueReplacing(String key, Function<String, String> replacer,
            Collection<String> exceptions) {
        return classicValueReplacing(key, replacer, exceptions, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

    public static MeterFilter classicValueReplacing(String key, Function<String, String> replacer,
            String... exceptions) {
        return classicValueReplacing(key, replacer, Arrays.asList(exceptions));
    }

    private static class ClassicFilter implements BiPredicate<String, String> {

        private final String matcher;

        private final Set<String> exceptions;

        public ClassicFilter(String matcher, Set<String> exceptions) {
            this.matcher = matcher;
            this.exceptions = exceptions;
        }

        @Override
        public boolean test(String key, String value) {
            return key.equals(matcher) && !exceptions.contains(value);
        }

    }

    private static class ValueReplacer implements BiFunction<String, String, Tag> {

        private final Function<String, String> delegate;

        public ValueReplacer(Function<String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Tag apply(String key, String value) {
            return Tag.of(key, delegate.apply(value));
        }

    }

}
