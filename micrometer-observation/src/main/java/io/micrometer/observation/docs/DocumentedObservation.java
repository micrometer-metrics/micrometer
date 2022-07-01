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
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;

/**
 * In order to describe your samples via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a sample. We can analyze the sources
 * and reuse this information to build a table of known metrics, their names and tags.
 *
 * We can generate documentation for all created samples but certain requirements need to
 * be met.
 *
 * <ul>
 * <li>Observations are grouped within an enum - the enum implements the
 * {@code DocumentedObservation} interface</li>
 * <li>If the observation contains {@link KeyName} then those need to be declared as
 * nested enums</li>
 * <li>The {@link DocumentedObservation#getHighCardinalityKeyNames()} need to call the
 * nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 * <li>The {@link DocumentedObservation#getLowCardinalityKeyNames()} need to call the
 * nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 * <li>Javadocs around enums will be used as description</li>
 * <li>If you want to merge different {@link KeyName} enum {@code values()} methods you
 * need to call the {@link KeyName#merge(KeyName[]...)} method</li>
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
     * Default technical name (e.g metric name). You can set the name either by this method or
     * {@link #getDefaultConvention()}. You can't use both.
     * @return name
     */
    default String getName() {
        return null;
    }

    /**
     *  Default naming convention (sets a technical name and key values). You can set the name either by this method or
     * {@link #getName()} ()}. You can't use both.
     * @return default naming convention
     */
    default Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
        return null;
    }

    /**
     * More human readable name available within the given context (e.g. span name).
     * @return contextual name
     */
    default String getContextualName() {
        return null;
    }

    /**
     * Low cardinality key names.
     * @return allowed tag keys for low cardinality key-values
     */
    default KeyName[] getLowCardinalityKeyNames() {
        return EMPTY;
    }

    /**
     * High cardinality key names.
     * @return allowed tag keys for high cardinality key-values
     */
    default KeyName[] getHighCardinalityKeyNames() {
        return EMPTY;
    }

    /**
     * Returns required prefix to be there for tags. For example, {@code foo.} would
     * require the tags to have a {@code foo.} prefix like this: {@code foo.bar=true}.
     * @return required prefix
     */
    default String getPrefix() {
        return "";
    }

    /**
     * Creates an {@link Observation}. You need to manually start it.
     * @param registry observation registry
     * @return observation
     */
    default Observation observation(ObservationRegistry registry) {
        return observation(registry, null);
    }

    /**
     * Creates an {@link Observation}. You need to manually start it.
     * @param registry observation registry
     * @param context observation context
     * @return observation
     */
    default Observation observation(ObservationRegistry registry, Observation.Context context) {
        return Observation.createNotStarted(getName(), context, registry).contextualName(getContextualName());
    }

    /**
     * Creates an {@link Observation} for the given {@link Observation.ObservationConvention}. You need to manually start it.
     * @param registry observation registry
     * @param context observation context
     * @param customConvention convention that (if not {@code null}) will override any pre-configured conventions
     * @param defaultConvention default convention that will be picked if there was neither custom convention nor a pre-configured one via {@link ObservationRegistry.ObservationConfig#observationConvention(Observation.ObservationConvention[])}
     * @return observation
     */
    default <T extends Observation.Context> Observation observation(ObservationRegistry registry, T context, @Nullable Observation.ObservationConvention<T> customConvention, @NonNull Observation.ObservationConvention<T> defaultConvention) {
        if (getDefaultConvention() == null) {
            throw new IllegalStateException("You've decided to use convention based naming yet this observation [" + getClass() + "] has not defined any default convention");
        }
        else if (!getDefaultConvention().isAssignableFrom(Objects.requireNonNull(defaultConvention, "You have not provided a default convention in the Observation factory method").getClass())) {
            throw new IllegalArgumentException("Observation [" + getClass() + "] defined default convention to be of type [" + getDefaultConvention() + "] but you have provided an incompatible one of type [" + defaultConvention.getClass() + "]");
        }
        return Observation.createNotStarted(registry, context, customConvention, defaultConvention).contextualName(getContextualName());
    }

    /**
     * Creates and starts an {@link Observation}.
     * @param registry observation registry
     * @return observation
     */
    default Observation start(ObservationRegistry registry) {
        return start(registry, null);
    }

    /**
     * Creates and starts an {@link Observation}.
     * @param registry observation registry
     * @param context observation context
     * @return observation
     */
    default Observation start(ObservationRegistry registry, Observation.Context context) {
        return observation(registry, context).start();
    }

}
