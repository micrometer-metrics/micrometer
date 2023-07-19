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
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.*;

import java.util.Objects;
import java.util.function.Supplier;

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
 * {@code ObservationDocumentation} interface</li>
 * <li>If the observation contains {@link KeyName} then those need to be declared as
 * nested enums</li>
 * <li>The {@link ObservationDocumentation#getHighCardinalityKeyNames()} need to call the
 * nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 * <li>The {@link ObservationDocumentation#getLowCardinalityKeyNames()} need to call the
 * nested enum's {@code values()} method to retrieve the array of allowed keys</li>
 * <li>Javadocs around enums will be used as description</li>
 * <li>If you want to merge different {@link KeyName} enum {@code values()} methods you
 * need to call the {@link KeyName#merge(KeyName[]...)} method</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface ObservationDocumentation {

    /**
     * Empty key names.
     */
    KeyName[] EMPTY = new KeyName[0];

    /**
     * Empty event names.
     */
    Observation.Event[] EMPTY_EVENT_NAMES = new Observation.Event[0];

    /**
     * Default technical name (e.g.: metric name). You can set the name either by this
     * method or {@link #getDefaultConvention()}. You can't use both.
     * @return name
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * Default naming convention (sets technical and contextual names, and key values).
     * You can set the names either by this method or {@link #getName()} and
     * {@link #getContextualName()}.
     * @return default naming convention
     */
    @Nullable
    default Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
        return null;
    }

    /**
     * More human-readable name available within the given context (e.g.: span name). You
     * can set the name either by this method or {@link #getDefaultConvention()}. This
     * method will override what {@link #getDefaultConvention()} has set.
     * @return contextual name
     */
    @Nullable
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
     * Event values.
     * @return allowed event values
     */
    default Observation.Event[] getEvents() {
        return EMPTY_EVENT_NAMES;
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
        return observation(registry, Observation.Context::new);
    }

    /**
     * Creates an {@link Observation}. You need to manually start it. When the
     * {@link ObservationRegistry} is null or the no-op registry, this fast returns a
     * no-op {@link Observation} and skips the creation of the
     * {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled by
     * an {@link ObservationPredicate}, a no-op observation will also be returned.
     * @param registry observation registry
     * @param contextSupplier observation context supplier
     * @return observation
     */
    default Observation observation(ObservationRegistry registry, Supplier<Observation.Context> contextSupplier) {
        Observation observation = Observation.createNotStarted(getName(), contextSupplier, registry);
        if (getContextualName() != null) {
            observation.contextualName(getContextualName());
        }
        return observation;
    }

    /**
     * Creates an {@link Observation} for the given {@link ObservationConvention}. You
     * need to manually start it. When the {@link ObservationRegistry} is null or the
     * no-op registry, this fast returns a no-op {@link Observation} and skips the
     * creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled by
     * an {@link ObservationPredicate}, a no-op observation will also be returned.
     * @param customConvention convention that (if not {@code null}) will override any
     * pre-configured conventions
     * @param defaultConvention default convention that will be picked if there was
     * neither custom convention nor a pre-configured one via
     * {@link ObservationRegistry.ObservationConfig#observationConvention(GlobalObservationConvention)}
     * @param contextSupplier observation context supplier
     * @param registry observation registry
     * @return observation
     */
    default <T extends Observation.Context> Observation observation(@Nullable ObservationConvention<T> customConvention,
            ObservationConvention<T> defaultConvention, Supplier<T> contextSupplier, ObservationRegistry registry) {
        if (getDefaultConvention() == null) {
            throw new IllegalStateException("You've decided to use convention based naming yet this observation ["
                    + getClass() + "] has not defined any default convention");
        }
        if (!getDefaultConvention().isAssignableFrom(
                Objects
                    .requireNonNull(defaultConvention,
                            "You have not provided a default convention in the Observation factory method")
                    .getClass())) {
            throw new IllegalArgumentException("Observation [" + getClass()
                    + "] defined default convention to be of type [" + getDefaultConvention()
                    + "] but you have provided an incompatible one of type [" + defaultConvention.getClass() + "]");
        }
        Observation observation = Observation.createNotStarted(customConvention, defaultConvention, contextSupplier,
                registry);
        if (getName() != null) {
            observation.getContext().setName(getName());
        }
        if (getContextualName() != null) {
            observation.contextualName(getContextualName());
        }
        return observation;
    }

    /**
     * Creates and starts an {@link Observation}.
     * @param registry observation registry
     * @return observation
     */
    default Observation start(ObservationRegistry registry) {
        return start(registry, Observation.Context::new);
    }

    /**
     * Creates and starts an {@link Observation}. When the {@link ObservationRegistry} is
     * null or the no-op registry, this fast returns a no-op {@link Observation} and skips
     * the creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled by
     * an {@link ObservationPredicate}, a no-op observation will also be returned.
     * @param registry observation registry
     * @param contextSupplier observation context supplier
     * @return observation
     */
    default Observation start(ObservationRegistry registry, Supplier<Observation.Context> contextSupplier) {
        return observation(registry, contextSupplier).start();
    }

    /**
     * Creates and starts an {@link Observation}. When the {@link ObservationRegistry} is
     * null or the no-op registry, this fast returns a no-op {@link Observation} and skips
     * the creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled by
     * an {@link ObservationPredicate}, a no-op observation will also be returned.
     * @param customConvention convention that (if not {@code null}) will override any
     * pre-configured conventions
     * @param defaultConvention default convention that will be picked if there was
     * neither custom convention nor a pre-configured one via
     * {@link ObservationRegistry.ObservationConfig#observationConvention(GlobalObservationConvention)}
     * @param contextSupplier observation context supplier
     * @param registry observation registry
     * @return observation
     */
    default <T extends Observation.Context> Observation start(@Nullable ObservationConvention<T> customConvention,
            ObservationConvention<T> defaultConvention, Supplier<T> contextSupplier, ObservationRegistry registry) {
        return observation(customConvention, defaultConvention, contextSupplier, registry).start();
    }

}
