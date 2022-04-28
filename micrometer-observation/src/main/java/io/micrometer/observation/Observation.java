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
package io.micrometer.observation;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;

/**
 * An act of viewing or noticing a fact or an occurrence for some scientific or other special purpose (According to dictionary.com).
 *
 * You can wrap an operation within your code in an {@link Observation} so that actions can take place within the lifecycle of
 * that observation via the {@link ObservationHandler}.
 *
 * According to what is configured the actions can be e.g. taking measurements via {@code Timer}, creating spans for distributed tracing,
 * correlating logs or just logging out additional information. You instrument your code once with an {@link Observation} but you can get
 * as many benefits out of it as many {@link ObservationHandler} you have.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface Observation {

    /**
     * No-op observation.
     */
    Observation NOOP = NoopObservation.INSTANCE;

    /**
     * Creates and starts an {@link Observation}.
     * When no registry is passed or observation is not applicable will return a no-op observation.
     *
     * @param name name of the observation
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(String name, @Nullable ObservationRegistry registry) {
        return start(name, null, registry);
    }

    /**
     * Creates and starts an {@link Observation}.
     * When no registry is passed or observation is not applicable will return a no-op observation.
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
     * When no registry is passed or observation is not applicable will return a no-op observation.
     *
     * @param name name of the observation
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, @Nullable ObservationRegistry registry) {
        return createNotStarted(name, null, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start.
     * When no registry is passed or observation is not applicable will return a no-op observation.
     *
     * @param name name of the observation
     * @param context mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, @Nullable Context context, @Nullable ObservationRegistry registry) {
        if (registry == null || !registry.observationConfig().isObservationEnabled(name, context) || registry.observationConfig().getObservationHandlers().isEmpty()) {
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
    Observation lowCardinalityKeyValue(KeyValue tag);

    /**
     * Sets a low cardinality tag. Low cardinality means that this tag
     * will have a bounded number of possible values. A templated HTTP URL is a good example
     * of such a tag (e.g. /foo/{userId}).
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation lowCardinalityKeyValue(String key, String value) {
        return lowCardinalityKeyValue(KeyValue.of(key, value));
    }

    /**
     * Sets a high cardinality tag. High cardinality means that this tag
     * will have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a tag (e.g. /foo/bar, /foo/baz etc.).
     *
     * @param tag tag
     * @return this
     */
    Observation highCardinalityKeyValue(KeyValue tag);

    /**
     * Sets a high cardinality tag. High cardinality means that this tag
     * will have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a tag (e.g. /foo/bar, /foo/baz etc.).
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation highCardinalityKeyValue(String key, String value) {
        return highCardinalityKeyValue(KeyValue.of(key, value));
    }

    /**
     * Checks whether this {@link Observation} is no-op.
     *
     * @return {@code true} when this is a no-op observation
     */
    default boolean isNoOp() {
        return this == NoopObservation.INSTANCE;
    }

    /**
     * Adds a key value provider that can be used to attach tags to the observation
     *
     * @param keyValuesProvider key value provider
     * @return this
     */
    Observation keyValuesProvider(KeyValuesProvider<?> keyValuesProvider);

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
     * Observes the passed {@link Runnable}, this means the followings:
     *
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link Runnable#run()}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * @param runnable the {@link Runnable} to run
     */
    @SuppressWarnings("unused")
    default void observe(Runnable runnable) {
        this.start();
        try (Scope scope = openScope()) {
            runnable.run();
        }
        catch (Exception exception) {
            this.error(exception);
            throw exception;
        }
        finally {
            this.stop();
        }
    }

    /**
     * Observes the passed {@link CheckedRunnable}, this means the followings:
     *
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link CheckedRunnable#run()}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * @param checkedRunnable the {@link CheckedRunnable} to run
     */
    @SuppressWarnings("unused")
    default void observeChecked(CheckedRunnable checkedRunnable) throws Exception {
        this.start();
        try (Scope scope = openScope()) {
            checkedRunnable.run();
        }
        catch (Exception exception) {
            this.error(exception);
            throw exception;
        }
        finally {
            this.stop();
        }
    }

    /**
     * Observes the passed {@link Supplier}, this means the followings:
     *
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link Supplier#get()}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * @param supplier the {@link Supplier} to call
     * @param <T> the type parameter of the {@link Supplier}
     * @return the result from {@link Supplier#get()}
     */
    @SuppressWarnings("unused")
    default <T> T observe(Supplier<T> supplier) {
        this.start();
        try (Scope scope = openScope()) {
            return supplier.get();
        }
        catch (Exception exception) {
            this.error(exception);
            throw exception;
        }
        finally {
            this.stop();
        }
    }

    /**
     * Observes the passed {@link Callable}, this means the followings:
     *
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link Callable#call()}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * @param callable the {@link Callable} to call
     * @param <T> the type parameter of the {@link Callable}
     * @return the result from {@link Callable#call()}
     */
    @SuppressWarnings("unused")
    default <T> T observeChecked(Callable<T> callable) throws Exception {
        this.start();
        try (Scope scope = openScope()) {
            return callable.call();
        }
        catch (Exception exception) {
            this.error(exception);
            throw exception;
        }
        finally {
            this.stop();
        }
    }

    /**
     * Wraps the given action in scope.
     *
     * @param action action to run
     */
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
     * Tries to run the action against an Observation. If the
     * Observation is null, we just run the action, otherwise
     * we run the action in scope.
     *
     * @param parent observation, potentially {@code null}
     * @param action action to run
     */
    static void tryScoped(@Nullable Observation parent, Runnable action) {
        if (parent != null) {
            parent.scoped(action);
        }
        else {
            action.run();
        }
    }

    /**
     * Tries to run the action against an Observation. If the
     * Observation is null, we just run the action, otherwise
     * we run the action in scope.
     *
     * @param parent observation, potentially {@code null}
     * @param action action to run
     * @return result of the action
     */
    static <T> T tryScoped(@Nullable Observation parent, Supplier<T> action) {
        if (parent != null) {
            return parent.scoped(action);
        }
        return action.get();
    }

    /**
     * Scope represent an action within which certain resources
     * (e.g. tracing context) are put in scope (e.g. in a ThreadLocal).
     * When the scope is closed the resources will be removed from the scope.
     *
     * @since 1.10.0
     */
    interface Scope extends AutoCloseable {

        /**
         * No-op scope.
         */
        Scope NOOP = NoopObservation.NoOpScope.INSTANCE;

        /**
         * Current observation available within this scope.
         *
         * @return current observation that this scope was created by
         */
        Observation getCurrentObservation();

        @Override
        void close();

        /**
         * Checks whether this {@link Scope} is no-op.
         *
         * @return {@code true} when this is a no-op scope
         */
        default boolean isNoOp() {
            return this == NoopObservation.NoOpScope.INSTANCE;
        }
    }

    /**
     * A mutable holder of data required by a {@link ObservationHandler}. When extended
     * you can provide your own, custom information to be processed by the handlers.
     *
     * @since 1.10.0
     */
    @SuppressWarnings("unchecked")
    class Context {
        private final Map<Object, Object> map = new HashMap<>();

        private String name;

        private String contextualName;

        @Nullable
        private Throwable error;

        private final Set<KeyValue> lowCardinalityKeyValues = new LinkedHashSet<>();

        private final Set<KeyValue> highCardinalityKeyValues = new LinkedHashSet<>();

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
         * Puts an element to the context.
         *
         * @param key key
         * @param object value
         * @param <T> value type
         * @return this for chaining
         */
        public <T> Context put(Object key, T object) {
            this.map.put(key, object);
            return this;
        }

        /**
         * Gets an entry from the context. Returns {@code null} when entry is not present.
         *
         * @param key key
         * @param <T> value type
         * @return entry or {@code null} if not present
         */
        @Nullable
        public <T> T get(Object key) {
            return (T) this.map.get(key);
        }

        /**
         * Removes an entry from the context.
         *
         * @param key key by which to remove an entry
         * @return the previous value associated with the key, or null if there was no mapping for the key
         */
        public Object remove(Object key) {
            return this.map.remove(key);
        }

        /**
         * Gets an entry from the context. Throws exception when entry is not present.
         *
         * @param key key
         * @param <T> value type
         * @return entry ot exception if not present
         */
        @NonNull
        public <T> T getRequired(Object key) {
            T object = (T) this.map.get(key);
            if (object == null) {
                throw new IllegalArgumentException("Context does not have an entry for key [" + key + "]");
            }
            return object;
        }

        /**
         * Checks if context contains a key.
         *
         * @param key key
         * @return {@code true} when the context contains the entry with the given key
         */
        public boolean containsKey(Object key) {
            return this.map.containsKey(key);
        }

        /**
         * Returns an element or default if not present.
         *
         * @param key key
         * @param defaultObject default object to return
         * @param <T> value type
         * @return object or default if not present
         */
        public <T> T getOrDefault(Object key, T defaultObject) {
            return (T) this.map.getOrDefault(key, defaultObject);
        }

        /**
         * Returns an element or calls a mapping function if entry not present.
         * The function will insert the value to the map.
         *
         * @param key key
         * @param mappingFunction mapping function
         * @param <T> value type
         * @return object or one derived from the mapping function if not present
         */
        public <T> T computeIfAbsent(Object key, Function<Object, ? extends T> mappingFunction) {
            return (T) this.map.computeIfAbsent(key, mappingFunction);
        }

        /**
         * Clears the entries from the context.
         */
        public void clear() {
            this.map.clear();
        }

        /**
         * Adds a low cardinality tag - those will be appended to those
         * fetched from the {@link KeyValuesProvider#getLowCardinalityKeyValues(Context)} method.
         *
         * @param tag a tag
         */
        void addLowCardinalityKeyValue(KeyValue tag) {
            this.lowCardinalityKeyValues.add(tag);
        }

        /**
         * Adds a high cardinality tag - those will be appended to those
         * fetched from the {@link KeyValuesProvider#getHighCardinalityKeyValues(Context)} method.
         *
         * @param tag a tag
         */
        void addHighCardinalityKeyValue(KeyValue tag) {
            this.highCardinalityKeyValues.add(tag);
        }

        /**
         * Adds multiple low cardinality key values at once.
         *
         * @param tags collection of tags
         */
        void addLowCardinalityKeyValues(KeyValues tags) {
            tags.stream().forEach(this::addLowCardinalityKeyValue);
        }

        /**
         * Adds multiple high cardinality key values at once.
         *
         * @param tags collection of tags
         */
        void addHighCardinalityKeyValues(KeyValues tags) {
            tags.stream().forEach(this::addHighCardinalityKeyValue);
        }

        @NonNull
        public KeyValues getLowCardinalityKeyValues() {
            return KeyValues.of(this.lowCardinalityKeyValues);
        }

        @NonNull
        public KeyValues getHighCardinalityKeyValues() {
            return KeyValues.of(this.highCardinalityKeyValues);
        }

        @NonNull
        public KeyValues getAllKeyValues() {
            return this.getLowCardinalityKeyValues().and(this.getHighCardinalityKeyValues());
        }

        @Override
        public String toString() {
            return "name='" + name + '\'' +
                    ", contextualName='" + contextualName + '\'' +
                    ", error='" + error + '\'' +
                    ", lowCardinalityKeyValues=" + toString(lowCardinalityKeyValues) +
                    ", highCardinalityKeyValues=" + toString(highCardinalityKeyValues) +
                    ", map=" + toString(map);
        }

        private String toString(Collection<KeyValue> tags) {
            return tags.stream()
                    .map(tag -> String.format("%s='%s'", tag.getKey(), tag.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        private String toString(Map<Object, Object> map) {
            return map.entrySet().stream()
                    .map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * Interface to be implemented by any object that wishes to be able
     * to update the default {@link KeyValuesProvider}.
     *
     * @param <T> {@link KeyValuesProvider} type
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface KeyValuesProviderAware<T extends KeyValuesProvider<?>> {
        /**
         * Overrides the default key values provider.
         *
         * @param keyValuesProvider key values provider
         */
        void setKeyValuesProvider(T keyValuesProvider);
    }

    /**
     * A provider of tags.
     *
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface KeyValuesProvider<T extends Context> {

        /**
         * Empty instance of the key-values provider.
         */
        KeyValuesProvider<Context> EMPTY = context -> false;

        /**
         * Low cardinality key values.
         *
         * @return key values
         */
        default KeyValues getLowCardinalityKeyValues(T context) {
            return KeyValues.empty();
        }

        /**
         * High cardinality key values.
         *
         * @return key values
         */
        default KeyValues getHighCardinalityKeyValues(T context) {
            return KeyValues.empty();
        }

        /**
         * Tells whether this key value provider should be applied for a given {@link Context}.
         *
         * @param context a {@link Context}
         * @return {@code true} when this key value provider should be used
         */
        boolean supportsContext(Context context);

        /**
         * Key value provider wrapping other key value providers.
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        class CompositeKeyValuesProvider implements KeyValuesProvider<Context> {

            private final List<KeyValuesProvider> keyValuesProviders;

            /**
             * Creates a new instance of {@code CompositeKeyValueProvider}.
             * @param keyValuesProviders the key value providers that are registered under the composite
             */
            public CompositeKeyValuesProvider(KeyValuesProvider... keyValuesProviders) {
                this(Arrays.asList(keyValuesProviders));
            }

            /**
             * Creates a new instance of {@code CompositeKeyValueProvider}.
             * @param keyValuesProviders the key value providers that are registered under the composite
             */
            public CompositeKeyValuesProvider(List<KeyValuesProvider> keyValuesProviders) {
                this.keyValuesProviders = keyValuesProviders;
            }

            @Override
            public KeyValues getLowCardinalityKeyValues(Context context) {
                return getProvidersForContext(context)
                        .map(provider -> provider.getLowCardinalityKeyValues(context))
                        .reduce(KeyValues::and)
                        .orElse(KeyValues.empty());
            }

            private Stream<KeyValuesProvider> getProvidersForContext(Context context) {
                return this.keyValuesProviders.stream().filter(provider -> provider.supportsContext(context));
            }

            @Override
            public KeyValues getHighCardinalityKeyValues(Context context) {
                return getProvidersForContext(context)
                        .map(provider -> provider.getHighCardinalityKeyValues(context))
                        .reduce(KeyValues::and)
                        .orElse(KeyValues.empty());
            }

            @Override
            public boolean supportsContext(Context context) {
                return this.keyValuesProviders.stream().anyMatch(provider -> provider.supportsContext(context));
            }

            /**
             * Returns the key value providers.
             * @return registered key value providers
             */
            public List<KeyValuesProvider> getKeyValueProviders() {
                return this.keyValuesProviders;
            }
        }
    }

    /**
     * A provider of tags that will be set on the {@link ObservationRegistry}.
     *
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface GlobalKeyValuesProvider<T extends Context> extends KeyValuesProvider<T> {

    }

    /**
     * A functional interface like {@link Runnable} but it can throw exceptions.
     */
    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }
}
