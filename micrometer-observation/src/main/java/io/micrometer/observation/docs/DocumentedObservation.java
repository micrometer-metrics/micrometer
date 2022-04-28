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
package io.micrometer.observation.docs;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

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
 *     <li>Observations are grouped within an enum - the enum implements the {@code DocumentedObservation} interface</li>
 *     <li>If the observation contains {@link KeyName} then those need to be declared as nested enums</li>
 *     <li>The {@link DocumentedObservation#getHighCardinalityKeyNames()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 *     <li>The {@link DocumentedObservation#getLowCardinalityKeyNames()} need to call the nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 *     <li>Javadocs around enums will be used as description</li>
 *     <li>If you want to merge different {@link KeyName} enum {@code values()} methods you need to call the {@link KeyName#merge(KeyName[]...)} method</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface DocumentedObservation {

    /**
     * Empty key names.
     */
    KeyName[] EMPTY = new KeyName[0];

    /**
     * Technical name (e.g metric name).
     *
     * @return name
     */
    String getName();

    /**
     * More human readable name available within the given context
     * (e.g. span name).
     *
     * @return contextual name
     */
    default String getContextualName() {
        return null;
    }

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
     * Low cardinality key names.
     *
     * @return allowed tag keys for low cardinality key-values
     */
    default KeyName[] getLowCardinalityKeyNames() {
        return EMPTY;
    }

    /**
     * High cardinality key names.
     *
     * @return allowed tag keys for high cardinality key-values
     */
    default KeyName[] getHighCardinalityKeyNames() {
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
     * Creates a {@link Observation}. You need to manually start it.
     *
     * @param registry observation registry
     * @return observation
     */
    default Observation observation(ObservationRegistry registry) {
        return observation(registry, null);
    }

    /**
     * Creates a {@link Observation}. You need to manually start it.
     *
     * @param registry observation registry
     * @param context observation context
     * @return observation
     */
    default Observation observation(ObservationRegistry registry, Observation.Context context) {
        return Observation.createNotStarted(getName(), context, registry)
                .contextualName(getContextualName());
    }

    /**
     * Creates and starts an {@link Observation}.
     *
     * @param registry observation registry
     * @return observation
     */
    default Observation start(ObservationRegistry registry) {
        return start(registry, null);
    }

    /**
     * Creates and starts an {@link Observation}.
     *
     * @param registry observation registry
     * @param context observation context
     * @return observation
     */
    default Observation start(ObservationRegistry registry, Observation.Context context) {
        return observation(registry, context).start();
    }
}
