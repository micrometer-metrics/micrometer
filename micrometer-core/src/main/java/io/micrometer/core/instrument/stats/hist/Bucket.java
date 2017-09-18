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
package io.micrometer.core.instrument.stats.hist;

import java.util.function.Function;

public class Bucket<T> {
    private final T tag;

    /**
     * The index of the bucket in its domain, prior to filtering
     */
    private final Integer index;

    /**
     * Cached string representation of {@code tag}.
     */
    private String tagStr;

    Long value = 0L;

    public Bucket(T tag, int index) {
        this.tag = tag;
        this.index = index;
    }

    public T getTag() {
        return tag;
    }

    public String getTagString() {
        return getTagString(Object::toString);
    }

    public String getTagString(Function<T, String> tagSerializer) {
        if(tagStr != null)
            return tagStr;
        tagStr = tagSerializer == null ? tag.toString() : tagSerializer.apply(tag);
        return tagStr;
    }

    public Bucket<T> increment() {
        this.value++;
        return this;
    }

    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Bucket{" +
            "tag=" + tag +
            ", value=" + value +
            '}';
    }

    public Integer getIndex() {
        return index;
    }
}
