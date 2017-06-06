/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.stats.hist;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public class Bucket<T> {
    private final T tag;

    /**
     * Cached string representation of {@code tag}.
     */
    private String tagStr;

    LongAdder value = new LongAdder();

    public Bucket(T tag) {
        this.tag = tag;
    }

    public Bucket(T tag, long initialValue) {
        this.tag = tag;
        this.value.add(initialValue);
    }

    public String getTag() {
        return getTag(Object::toString);
    }

    public String getTag(Function<T, String> tagSerializer) {
        if(tagStr != null)
            return tagStr;
        tagStr = tagSerializer == null ? tag.toString() : tagSerializer.apply(tag);
        return tagStr;
    }

    public Bucket<T> increment() {
        this.value.increment();
        return this;
    }

    public double getValue() {
        return value.doubleValue();
    }
}
