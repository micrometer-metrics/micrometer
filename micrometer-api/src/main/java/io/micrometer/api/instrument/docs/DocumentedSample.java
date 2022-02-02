/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.api.instrument.docs;

import io.micrometer.api.instrument.Timer;

/**
 * In order to describe your samples via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a sample. We can
 * analyze the sources and reuse this information to build a table of known metrics, their
 * names and tags.
 *
 * We can generate documentation for all created samples but certain requirements need to be
 * met.
 *
 * <ul>
 *     <li>Metrics are grouped within an enum - the enum implements the {@code DocumentedSample} interface</li>
 *     <li>If the sample contains {@link TagKey} then those need to be declared as nested enums</li>
 *     <li>The {@link DocumentedSample#getHighCardinalityTagKeys()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 *     <li>The {@link DocumentedSample#getLowCardinalityTagKeys()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 *     <li>Javadocs around enums will be used as description</li>
 *     <li>If you want to merge different {@link TagKey} enum {@code values()} methods you need to call the {@link TagKey#merge(TagKey[]...)} method</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface DocumentedSample {

    /**
     * Empty tag keys.
     */
    TagKey[] EMPTY = new TagKey[0];

    /**
     * Metric name.
     *
     * @return metric name
     */
    String getName();

    /**
     * Low cardinality tags.
     *
     * @return allowed tag keys for low cardinality tags
     */
    default TagKey[] getLowCardinalityTagKeys() {
        return EMPTY;
    }

    /**
     * High cardinality tags.
     *
     * @return allowed tag keys for high cardinality tags
     */
    default TagKey[] getHighCardinalityTagKeys() {
        return EMPTY;
    }

    /**
     * Returns required prefix to be there for tags. For example, {@code foo.} would
     * require the tags to have a {@code foo.} prefix like this:
     * {@code foo.bar=true}.
     *
     * @return required prefix
     */
    default String getPrefix() {
        return "";
    }

    /**
     * Creates a {@link Timer.Builder}.
     *
     * @return timer builder
     */
    default Timer.Builder toBuilder() {
       return Timer.builder(getName());
    }
}
