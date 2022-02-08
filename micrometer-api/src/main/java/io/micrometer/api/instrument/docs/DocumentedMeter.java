/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.api.instrument.docs;

import io.micrometer.api.instrument.Meter;
import io.micrometer.api.lang.Nullable;

/**
 * In order to describe your metrics via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a metric.
 *
 * We can generate documentation for all created metrics but certain requirements need to be
 * met
 *
 * <ul>
 *     <li>Metrics are grouped within an enum - the enum implements the {@link DocumentedMeter} interface</li>
 *     <li>If the span contains {@link TagKey} then those need to be declared as nested enums</li>
 *     <li>The {@link DocumentedMeter#getTagKeys()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys / events</li>
 *     <li>Javadocs around enums will be used as description</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface DocumentedMeter {
    /**
     * Empty tag keys.
     */
    TagKey[] EMPTY_TAGS = new TagKey[0];

    /**
     * Metric name.
     *
     * @return metric name
     */
    String getName();

    /**
     * Base unit.
     *
     * @return base unit
     */
    @Nullable
    default String getBaseUnit() {
        return null;
    }

    /**
     * Type of this metric.
     *
     * @return meter type
     */
    Meter.Type getType();

    /**
     * Builds a name from provided vars. Follows the {@link String#format(String, Object...)} patterns.
     *
     * @param vars variables to pass to {@link String#format(String, Object...)}
     * @return constructed name
     */
    default String getName(String... vars) {
        if (getName().contains("%s")) {
            return String.format(getName(), (Object[]) vars);
        }
        return getName();
    }

    /**
     * Allowed tag keys.
     *
     * @return allowed tag keys - if set will override any tag keys coming from {@link DocumentedMeter#overridesDefaultMetricFrom()}
     */
    default TagKey[] getTagKeys() {
        return EMPTY_TAGS;
    }

    /**
     * Additional tag keys.
     *
     * @return additional tag keys - if set will append any tag keys coming from {@link DocumentedMeter#overridesDefaultMetricFrom()}
     */
    default TagKey[] getAdditionalTagKeys() {
        return EMPTY_TAGS;
    }

    /**
     * Override this when custom metric should be documented instead of the default one.
     *
     * @return {@link DocumentedObservation} for which you don't want to create a default metric documentation
     */
    default DocumentedObservation overridesDefaultMetricFrom() {
        return null;
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

}
