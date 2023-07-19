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

package io.micrometer.core.instrument.docs;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;

/**
 * In order to describe your metrics via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a metric.
 *
 * We can generate documentation for all created metrics but certain requirements need to
 * be met
 *
 * <ul>
 * <li>Metrics are grouped within an enum - the enum implements the
 * {@link MeterDocumentation} interface</li>
 * <li>If the span contains {@link KeyName} then those need to be declared as nested
 * enums</li>
 * <li>The {@link MeterDocumentation#getKeyNames()} need to call the nested enum's
 * {@code values()} method to retrieve the array of allowed keys / events</li>
 * <li>Javadocs around enums will be used as description</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface MeterDocumentation {

    /**
     * Empty key names.
     */
    KeyName[] EMPTY = new KeyName[0];

    /**
     * Metric name.
     * @return metric name
     */
    String getName();

    /**
     * Base unit.
     * @return base unit
     */
    @Nullable
    default String getBaseUnit() {
        return null;
    }

    /**
     * Type of this metric.
     * @return meter type
     */
    Meter.Type getType();

    /**
     * Builds a name from provided vars. Follows the
     * {@link String#format(String, Object...)} patterns.
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
     * Allowed key names.
     * @return allowed key names - if set will override any key names coming from
     * {@link MeterDocumentation#overridesDefaultMetricFrom()}
     */
    default KeyName[] getKeyNames() {
        return EMPTY;
    }

    /**
     * Additional key names.
     * @return additional key names - if set will append any key names coming from
     * {@link MeterDocumentation#overridesDefaultMetricFrom()}
     */
    default KeyName[] getAdditionalKeyNames() {
        return EMPTY;
    }

    /**
     * Override this when custom metric should be documented instead of the default one.
     * Requires the Observation module on the classpath.
     * @return {@link MeterDocumentation} for which you don't want to create a default
     * metric documentation
     */
    default Enum<?> overridesDefaultMetricFrom() {
        return null;
    }

    /**
     * Returns required prefix to be there for tags. For example, {@code foo.} would
     * require the tags to have a {@code foo.} prefix like this: {@code foo.bar=true}.
     * @return required prefix
     */
    default String getPrefix() {
        return "";
    }

}
