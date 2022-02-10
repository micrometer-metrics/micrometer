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
package io.micrometer.api.instrument.observation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.TagsProvider;
import io.micrometer.api.instrument.Timer;
import io.micrometer.api.lang.NonNull;
import io.micrometer.api.lang.Nullable;

/**
 * An act of viewing or noticing a fact or an occurrence for some scientific or other special purpose (According to dictionary.com).
 *
 * You can wrap an operation within your code in an {@link Observation} so that actions can take place within the lifecycle of
 * that observation via the {@link ObservationHandler}.
 *
 * According to what is configured the actions can be e.g. taking measurements via {@link Timer}, creating spans for distributed tracing,
 * correlating logs or just logging out additional information. You instrument your code once with an {@link Observation} but you can get
 * as many benefits out of it as many {@link ObservationHandler} you have.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface Observation {

    /**
     * Creates and starts an {@link Observation}.
     *
     * @param name name of the observation
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(String name, ObservationRegistry registry) {
        return start(name, null, registry);
    }

    /**
     * Creates and starts an {@link Observation}.
     *
     * @param name name of the observation
     * @param context mutable context
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(String name, @Nullable Context context, ObservationRegistry registry) {
        return createNotStarted(name, context, registry).start();
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start.
     *
     * @param name name of the observation
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, ObservationRegistry registry) {
        return createNotStarted(name, null, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start.
     *
     * @param name name of the observation
     * @param context mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, @Nullable Context context, ObservationRegistry registry) {
        if (!registry.observationConfig().isObservationEnabled(name, context)) {
            return NoopObservation.INSTANCE;
        }
        return new SimpleObservation(name, registry, context == null ? new Context() : context);
    }

    /**
     * Sets the name that can be defined from the contents of the context.
     * E.g. a span name should not be the default observation name but one coming from
     * an HTTP request.
     *
     * @param contextualName contextual name
     * @return this
     */
    Observation contextualName(String contextualName);

    /**
     * Sets a low cardinality tag. Low cardinality means that this tag
     * will have a bounded number of possible values. A templated HTTP URL is a good example
     * of such a tag (e.g. /foo/{userId}).
     *
     * @param tag tag
     * @return this
     */
    Observation lowCardinalityTag(Tag tag);

    /**
     * Sets a low cardinality tag. Low cardinality means that this tag
     * will have a bounded number of possible values. A templated HTTP URL is a good example
     * of such a tag (e.g. /foo/{userId}).
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation lowCardinalityTag(String key, String value) {
        return lowCardinalityTag(Tag.of(key, value));
    }

    /**
     * Sets a high cardinality tag. High cardinality means that this tag
     * will have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a tag (e.g. /foo/bar, /foo/baz etc.).
     *
     * @param tag tag
     * @return this
     */
    Observation highCardinalityTag(Tag tag);

    /**
     * Sets a high cardinality tag. High cardinality means that this tag
     * will have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a tag (e.g. /foo/bar, /foo/baz etc.).
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation highCardinalityTag(String key, String value) {
        return highCardinalityTag(Tag.of(key, value));
    }

    /**
     * Sets an error.
     *
     * @param error error
     * @return this
     */
    Observation error(Throwable error);

    /**
     * Starts the observation. Remember to call this method, otherwise
     * timing calculations will not take place.
     *
     * @return this
     */
    Observation start();

    /**
     * Stop the observation. Remember to call this method, otherwise
     * timing calculations won't be finished.
     */
    void stop();

    /**
     * When put in scope, additional operations can take place by the
     * {@link ObservationHandler}s such as putting entries in thread local.
     *
     * @return new scope
     */
    Scope openScope();

    /**
     * Wraps the given action in scope.
     *
     * @param action action to run
     */
    default void scoped(Runnable action) {
        try (Scope scope = openScope()) {
            action.run();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
    }

    /**
     * Wraps the given action in scope.
     *
     * @param action action to run
     * @return result of the action
     */
    default <T> T scoped(Supplier<T> action) {
        try (Scope scope = openScope()) {
            return action.get();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
    }

    /**
     * Scope represent an action within which certain resources
     * (e.g. tracing context) are put in scope (e.g. in a ThreadLocal).
     * When the scope is closed the resources will be removed from the scope.
     *
     * @since 2.0.0
     */
    interface Scope extends AutoCloseable {
        /**
         * Current observation available within this scope.
         *
         * @return current observation that this scope was created by
         */
        Observation getCurrentObservation();

        @Override
        void close();
    }

    /**
     * A mutable holder of data required by a {@link ObservationHandler}. When extended
     * you can provide your own, custom information to be processed by the handlers.
     *
     * @since 2.0.0
     */
    @SuppressWarnings("unchecked")
    class Context implements TagsProvider {
        private final Map<Class<?>, Object> map = new HashMap<>();

        private String name;

        private String contextualName;

        @Nullable
        private Throwable error;

        private final Set<Tag> additionalLowCardinalityTags = new LinkedHashSet<>();

        private final Set<Tag> additionalHighCardinalityTags = new LinkedHashSet<>();

        /**
         * Puts an element to the context.
         *
         * @param clazz key
         * @param object value
         * @param <T> type of value
         * @return this for chaining
         */
        public <T> Context put(Class<T> clazz, T object) {
            this.map.put(clazz, object);
            return this;
        }

        /**
         * The observation name.
         *
         * @return name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Sets the observation name.
         *
         * @param name observation name
         * @return this for chaining
         */
        public Context setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Returns the contextual name. The name that makes sense within
         * the current context (e.g. name derived from HTTP request).
         *
         * @return contextual name
         */
        public String getContextualName() {
            return this.contextualName;
        }

        /**
         * Sets the contextual name.
         *
         * @param contextualName name
         * @return this for chaining
         */
        public Context setContextualName(String contextualName) {
            this.contextualName = contextualName;
            return this;
        }

        /**
         * Optional error that occurred while processing the {@link Observation}.
         *
         * @return optional error
         */
        public Optional<Throwable> getError() {
            return Optional.ofNullable(this.error);
        }

        /**
         * Sets an error that occurred while processing the {@link Observation}.
         *
         * @param error error
         * @return this for chaining
         */
        public Context setError(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Removes an entry from the context.
         *
         * @param clazz key by which to remove an entry
         */
        public void remove(Class<?> clazz) {
            this.map.remove(clazz);
        }

        /**
         * Gets an entry from the context. Returns {@code null} when entry is not present.
         *
         * @param clazz key
         * @param <T> key type
         * @return entry or {@code null} if not present
         */
        @Nullable
        public <T> T get(Class<T> clazz) {
            return (T) this.map.get(clazz);
        }

        /**
         * Gets an entry from the context. Throws exception when entry is not present.
         *
         * @param clazz key
         * @param <T> key type
         * @return entry ot exception if not present
         */
        @NonNull
        public <T> T getRequired(Class<T> clazz) {
            T object = (T) this.map.get(clazz);
            if (object == null) {
                throw new IllegalArgumentException("Context does not have an entry for key [" + clazz + "]");
            }
            return object;
        }

        /**
         * Checks if context contains a key.
         *
         * @param clazz key
         * @param <T> key type
         * @return {@code true} when the context contains the entry with the given key
         */
        public <T> boolean containsKey(Class<T> clazz) {
            return this.map.containsKey(clazz);
        }

        /**
         * Returns an element or default if not present.
         *
         * @param clazz key
         * @param defaultObject default object to return
         * @param <T> object type
         * @return object or default if not present
         */
        public <T> T getOrDefault(Class<T> clazz, T defaultObject) {
            return (T) this.map.getOrDefault(clazz, defaultObject);
        }

        /**
         * Returns an element or calls a mapping function if entry not present.
         * The function will insert the value to the map.
         *
         * @param clazz key
         * @param mappingFunction mapping function
         * @param <T> object type
         * @return object or one derrived from the mapping function if not present
         */
        public <T> T computeIfAbsent(Class<T> clazz, Function<Class<?>, ? extends T> mappingFunction) {
            return (T) this.map.computeIfAbsent(clazz, mappingFunction);
        }

        /**
         * Clears the entries from the context.
         */
        public void clear() {
            this.map.clear();
        }

        /**
         * Adds an additional low cardinality tag - those will be appended to those
         * passed via the {@link TagsProvider#getLowCardinalityTags()} method.
         *
         * @param tag a tag
         */
        public void addLowCardinalityTag(Tag tag) {
            this.additionalLowCardinalityTags.add(tag);
        }

        /**
         * Adds an additional high cardinality tag - those will be appended to those
         * passed via the {@link TagsProvider#getLowCardinalityTags()} ()} method.
         *
         * @param tag a tag
         */
        public void addHighCardinalityTag(Tag tag) {
            this.additionalHighCardinalityTags.add(tag);
        }

        @Override
        @NonNull
        public Tags getAdditionalLowCardinalityTags() {
            return Tags.of(this.additionalLowCardinalityTags);
        }

        @Override
        @NonNull
        public Tags getAdditionalHighCardinalityTags() {
            return Tags.of(this.additionalHighCardinalityTags);
        }

        @Override
        public String toString() {
            return "Context{" +
                    "map=" + map +
                    ", name='" + name + '\'' +
                    ", contextualName='" + contextualName + '\'' +
                    ", lowCardinalityTags=" + getLowCardinalityTags() +
                    ", additionalLowCardinalityTags=" + additionalLowCardinalityTags +
                    ", highCardinalityTags=" + getHighCardinalityTags() +
                    ", additionalHighCardinalityTags=" + additionalHighCardinalityTags +
                    '}';
        }
    }
}
