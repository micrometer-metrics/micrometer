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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An act of viewing or noticing a fact or an occurrence for some scientific or other
 * special purpose (According to dictionary.com).
 *
 * You can wrap an operation within your code in an {@link Observation} so that actions
 * can take place within the lifecycle of that observation via the
 * {@link ObservationHandler}.
 *
 * According to what is configured the actions can be e.g. taking measurements via
 * {@code Timer}, creating spans for distributed tracing, correlating logs or just logging
 * out additional information. You instrument your code once with an {@link Observation}
 * but you can get as many benefits out of it as many {@link ObservationHandler} you have.
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
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * @param name name of the observation
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(String name, ObservationRegistry registry) {
        return start(name, null, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * @param name name of the observation
     * @param context mutable context
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(String name, @Nullable Context context, @Nullable ObservationRegistry registry) {
        return createNotStarted(name, context, registry).start();
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * @param name name of the observation
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, @Nullable ObservationRegistry registry) {
        return createNotStarted(name, null, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * @param name name of the observation
     * @param context mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(String name, @Nullable Context context,
            @Nullable ObservationRegistry registry) {
        if (registry == null || registry.isNoop()
                || !registry.observationConfig().isObservationEnabled(name, context)) {
            return NoopObservation.INSTANCE;
        }
        return new SimpleObservation(name, registry, context == null ? new Context() : context);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation. Allows to set a custom {@link ObservationConvention} and requires to
     * provide a default one if a neither a custom nor a pre-configured one (via
     * {@link ObservationRegistry.ObservationConfig#getObservationConvention(Context, ObservationConvention)})
     * was found.
     * @param <T> type of context
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a configured one was found
     * @param context the observation context
     * @param registry observation registry
     * @return created but not started observation
     */
    static <T extends Observation.Context> Observation createNotStarted(
            @Nullable Observation.ObservationConvention<T> customConvention,
            @NonNull Observation.ObservationConvention<T> defaultConvention, @NonNull T context,
            @NonNull ObservationRegistry registry) {
        Observation.ObservationConvention<T> convention;
        if (customConvention != null) {
            convention = customConvention;
        }
        else {
            convention = registry.observationConfig().getObservationConvention(context, defaultConvention);
        }
        return Observation.createNotStarted(convention, context, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * @param observationConvention observation convention
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(ObservationConvention<?> observationConvention, @Nullable ObservationRegistry registry) {
        return start(observationConvention, null, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * @param observationConvention observation convention
     * @param context mutable context
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(ObservationConvention<?> observationConvention, @Nullable Context context,
            @Nullable ObservationRegistry registry) {
        return createNotStarted(observationConvention, context, registry).start();
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation. Allows to set a
     * custom {@link ObservationConvention} and requires to provide a default one if a
     * neither a custom nor a pre-configured one (via
     * {@link ObservationRegistry.ObservationConfig#getObservationConvention(Context, ObservationConvention)})
     * was found.
     * @param <T> type of context
     * @param registry observation registry
     * @param context the observation context
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a configured one was found
     * @return started observation
     */
    static <T extends Observation.Context> Observation start(
            @Nullable Observation.ObservationConvention<T> customConvention,
            @NonNull Observation.ObservationConvention<T> defaultConvention, @NonNull T context,
            @NonNull ObservationRegistry registry) {
        return createNotStarted(customConvention, defaultConvention, context, registry).start();
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * @param observationConvention observation convention
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(ObservationConvention<?> observationConvention,
            @Nullable ObservationRegistry registry) {
        return createNotStarted(observationConvention, null, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * @param observationConvention observation convention
     * @param context mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(ObservationConvention<?> observationConvention, @Nullable Context context,
            @Nullable ObservationRegistry registry) {
        if (registry == null || registry.isNoop()
                || !registry.observationConfig().isObservationEnabled(observationConvention.getName(), context)
                || observationConvention == NoopObservationConvention.INSTANCE) {
            return NoopObservation.INSTANCE;
        }
        return new SimpleObservation(observationConvention, registry, context == null ? new Context() : context);
    }

    /**
     * Sets the name that can be defined from the contents of the context. E.g. a span
     * name should not be the default observation name but one coming from an HTTP
     * request.
     * @param contextualName contextual name
     * @return this
     */
    Observation contextualName(String contextualName);

    /**
     * If you have access to a previously created {@link Observation} you can manually set
     * the parent {@link Observation} using this method - that way you won't need to open
     * scopes just to create a child observation.
     *
     * If you're using the {@link #openScope()} method then the parent observation will be
     * automatically set, and you don't have to call this method.
     * @param parentObservation parent observation to set
     * @return this
     */
    Observation parentObservation(Observation parentObservation);

    /**
     * Adds a low cardinality key value. Low cardinality means that this key value will
     * have a bounded number of possible values. A templated HTTP URL is a good example of
     * such a key value (e.g. /foo/{userId}).
     * @param keyValue key value
     * @return this
     */
    Observation lowCardinalityKeyValue(KeyValue keyValue);

    /**
     * Adds a low cardinality key value. Low cardinality means that this key value will
     * have a bounded number of possible values. A templated HTTP URL is a good example of
     * such a key value (e.g. /foo/{userId}).
     * @param key key
     * @param value value
     * @return this
     */
    default Observation lowCardinalityKeyValue(String key, String value) {
        return lowCardinalityKeyValue(KeyValue.of(key, value));
    }

    /**
     * Adds multiple low cardinality key value instances. Low cardinality means that the
     * key value will have a bounded number of possible values. A templated HTTP URL is a
     * good example of such a key value (e.g. /foo/{userId}).
     * @param keyValues key value instances
     * @return this
     */
    default Observation lowCardinalityKeyValues(KeyValues keyValues) {
        keyValues.stream().forEach(this::lowCardinalityKeyValue);
        return this;
    }

    /**
     * Adds a high cardinality key value. High cardinality means that this key value will
     * have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a key value (e.g. /foo/bar, /foo/baz etc.).
     * @param keyValue key value
     * @return this
     */
    Observation highCardinalityKeyValue(KeyValue keyValue);

    /**
     * Adds a high cardinality key value. High cardinality means that this key value will
     * have possible an unbounded number of possible values. An HTTP URL is a good example
     * of such a key value (e.g. /foo/bar, /foo/baz etc.).
     * @param key key
     * @param value value
     * @return this
     */
    default Observation highCardinalityKeyValue(String key, String value) {
        return highCardinalityKeyValue(KeyValue.of(key, value));
    }

    /**
     * Adds multiple high cardinality key value instances. High cardinality means that the
     * key value will have possible an unbounded number of possible values. An HTTP URL is
     * a good example of such a key value (e.g. /foo/bar, /foo/baz etc.).
     * @param keyValues key value instances
     * @return this
     */
    default Observation highCardinalityKeyValues(KeyValues keyValues) {
        keyValues.stream().forEach(this::highCardinalityKeyValue);
        return this;
    }

    /**
     * Checks whether this {@link Observation} is no-op.
     * @return {@code true} when this is a no-op observation
     */
    default boolean isNoop() {
        return this == NoopObservation.INSTANCE;
    }

    /**
     * Adds a key values provider that can be used to attach key values to the observation
     * @param keyValuesProvider key values provider
     * @return this
     */
    Observation keyValuesProvider(KeyValuesProvider<?> keyValuesProvider);

    /**
     * Signals an error.
     * @param error error
     * @return this
     */
    Observation error(Throwable error);

    /**
     * Signals an arbitrary {@link Event}.
     * @param event event
     * @return this
     */
    Observation event(Event event);

    /**
     * Starts the observation. Remember to call this method, otherwise timing calculations
     * will not take place.
     * @return this
     */
    Observation start();

    /**
     * Returns the context attached to this observation.
     * @return corresponding context
     */
    ContextView getContext();

    /**
     * Stop the observation. Remember to call this method, otherwise timing calculations
     * won't be finished.
     */
    void stop();

    /**
     * When put in scope, additional operations can take place by the
     * {@link ObservationHandler}s such as putting entries in thread local.
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
     * @param checkedRunnable the {@link CheckedRunnable} to run
     */
    @SuppressWarnings("unused")
    default void observeChecked(CheckedRunnable checkedRunnable) throws Throwable {
        this.start();
        try (Scope scope = openScope()) {
            checkedRunnable.run();
        }
        catch (Throwable error) {
            this.error(error);
            throw error;
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
     * Observes the passed {@link CheckedCallable}, this means the followings:
     *
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link CheckedCallable#call()}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     * @param checkedCallable the {@link CheckedCallable} to call
     * @param <T> the type parameter of the {@link CheckedCallable}
     * @return the result from {@link CheckedCallable#call()}
     */
    @SuppressWarnings("unused")
    default <T> T observeChecked(CheckedCallable<T> checkedCallable) throws Throwable {
        this.start();
        try (Scope scope = openScope()) {
            return checkedCallable.call();
        }
        catch (Throwable error) {
            this.error(error);
            throw error;
        }
        finally {
            this.stop();
        }
    }

    /**
     * Wraps the given action in scope.
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
     * Tries to run the action against an Observation. If the Observation is null, we just
     * run the action, otherwise we run the action in scope.
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
     * Tries to run the action against an Observation. If the Observation is null, we just
     * run the action, otherwise we run the action in scope.
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
     * Scope represent an action within which certain resources (e.g. tracing context) are
     * put in scope (e.g. in a ThreadLocal). When the scope is closed the resources will
     * be removed from the scope.
     *
     * @since 1.10.0
     */
    interface Scope extends AutoCloseable {

        /**
         * No-op scope.
         */
        Scope NOOP = NoopObservation.NoopScope.INSTANCE;

        /**
         * Current observation available within this scope.
         * @return current observation that this scope was created by
         */
        Observation getCurrentObservation();

        @Override
        void close();

        /**
         * Checks whether this {@link Scope} is no-op.
         * @return {@code true} when this is a no-op scope
         */
        default boolean isNoop() {
            return this == NoopObservation.NoopScope.INSTANCE;
        }

    }

    /**
     * A mutable holder of data required by an {@link ObservationHandler}. When extended
     * you can provide your own, custom information to be processed by the handlers.
     *
     * @since 1.10.0
     */
    @SuppressWarnings("unchecked")
    class Context implements ContextView {

        private final Map<Object, Object> map = new HashMap<>();

        private String name;

        @Nullable
        private String contextualName;

        @Nullable
        private Throwable error;

        @Nullable
        private Observation parentObservation;

        private final Set<KeyValue> lowCardinalityKeyValues = new LinkedHashSet<>();

        private final Set<KeyValue> highCardinalityKeyValues = new LinkedHashSet<>();

        /**
         * The observation name.
         * @return name
         */
        @Override
        public String getName() {
            return this.name;
        }

        /**
         * Sets the observation name.
         * @param name observation name
         * @return this for chaining
         */
        public Context setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Returns the contextual name. The name that makes sense within the current
         * context (e.g. name derived from HTTP request).
         * @return contextual name
         */
        @Override
        public String getContextualName() {
            return this.contextualName;
        }

        /**
         * Sets the contextual name.
         * @param contextualName name
         * @return this for chaining
         */
        public Context setContextualName(String contextualName) {
            this.contextualName = contextualName;
            return this;
        }

        /**
         * Returns the parent {@link Observation}.
         * @return parent observation or {@code null} if there was no parent
         */
        @Override
        @Nullable
        public Observation getParentObservation() {
            return parentObservation;
        }

        /**
         * Sets the parent {@link Observation}.
         * @param parentObservation parent observation to set
         */
        public void setParentObservation(@Nullable Observation parentObservation) {
            this.parentObservation = parentObservation;
        }

        /**
         * Optional error that occurred while processing the {@link Observation}.
         * @return optional error
         */
        @Override
        public Optional<Throwable> getError() {
            return Optional.ofNullable(this.error);
        }

        /**
         * Sets an error that occurred while processing the {@link Observation}.
         * @param error error
         * @return this for chaining
         */
        public Context setError(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Puts an element to the context.
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
         * @param key key
         * @param <T> value type
         * @return entry or {@code null} if not present
         */
        @Override
        @Nullable
        public <T> T get(Object key) {
            return (T) this.map.get(key);
        }

        /**
         * Removes an entry from the context.
         * @param key key by which to remove an entry
         * @return the previous value associated with the key, or null if there was no
         * mapping for the key
         */
        public Object remove(Object key) {
            return this.map.remove(key);
        }

        /**
         * Gets an entry from the context. Throws exception when entry is not present.
         * @param key key
         * @param <T> value type
         * @return entry ot exception if not present
         */
        @Override
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
         * @param key key
         * @return {@code true} when the context contains the entry with the given key
         */
        @Override
        public boolean containsKey(Object key) {
            return this.map.containsKey(key);
        }

        /**
         * Returns an element or default if not present.
         * @param key key
         * @param defaultObject default object to return
         * @param <T> value type
         * @return object or default if not present
         */
        @Override
        public <T> T getOrDefault(Object key, T defaultObject) {
            return (T) this.map.getOrDefault(key, defaultObject);
        }

        /**
         * Returns an element or calls a mapping function if entry not present. The
         * function will insert the value to the map.
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
         * Adds a low cardinality key value - those will be appended to those fetched from
         * the {@link KeyValuesProvider#getLowCardinalityKeyValues(Context)} method.
         * @param keyValue a key value
         */
        void addLowCardinalityKeyValue(KeyValue keyValue) {
            this.lowCardinalityKeyValues.add(keyValue);
        }

        /**
         * Adds a high cardinality key value - those will be appended to those fetched
         * from the {@link KeyValuesProvider#getHighCardinalityKeyValues(Context)} method.
         * @param keyValue a key value
         */
        void addHighCardinalityKeyValue(KeyValue keyValue) {
            this.highCardinalityKeyValues.add(keyValue);
        }

        /**
         * Adds multiple low cardinality key values at once.
         * @param keyValues collection of key values
         */
        void addLowCardinalityKeyValues(KeyValues keyValues) {
            keyValues.stream().forEach(this::addLowCardinalityKeyValue);
        }

        /**
         * Adds multiple high cardinality key values at once.
         * @param keyValues collection of key values
         */
        void addHighCardinalityKeyValues(KeyValues keyValues) {
            keyValues.stream().forEach(this::addHighCardinalityKeyValue);
        }

        @NonNull
        @Override
        public KeyValues getLowCardinalityKeyValues() {
            return KeyValues.of(this.lowCardinalityKeyValues);
        }

        @NonNull
        @Override
        public KeyValues getHighCardinalityKeyValues() {
            return KeyValues.of(this.highCardinalityKeyValues);
        }

        @NonNull
        @Override
        public KeyValues getAllKeyValues() {
            return this.getLowCardinalityKeyValues().and(this.getHighCardinalityKeyValues());
        }

        @Override
        public String toString() {
            return "name='" + name + '\'' + ", contextualName='" + contextualName + '\'' + ", error='" + error + '\''
                    + ", lowCardinalityKeyValues=" + toString(lowCardinalityKeyValues) + ", highCardinalityKeyValues="
                    + toString(highCardinalityKeyValues) + ", map=" + toString(map);
        }

        private String toString(Collection<KeyValue> keyValues) {
            return keyValues.stream().map(keyValue -> String.format("%s='%s'", keyValue.getKey(), keyValue.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        private String toString(Map<Object, Object> map) {
            return map.entrySet().stream().map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

    }

    class Event {

        private final String name;

        @Nullable
        private final String contextualName;

        public Event(String name) {
            this(name, null);
        }

        public Event(String name, @Nullable String contextualName) {
            this.name = name;
            this.contextualName = contextualName;
        }

        public String getName() {
            return this.name;
        }

        @Nullable
        public String getContextualName() {
            return this.contextualName;
        }

        @Override
        public String toString() {
            return "event.name='" + this.name + "', event.contextualName='" + this.contextualName + '\'';
        }

    }

    /**
     * Read only view on the {@link Context}.
     */
    interface ContextView {

        /**
         * The observation name.
         * @return name
         */
        String getName();

        /**
         * Returns the contextual name. The name that makes sense within the current
         * context (e.g. name derived from HTTP request).
         * @return contextual name
         */
        @Nullable
        String getContextualName();

        /**
         * Returns the parent {@link Observation}.
         * @return parent observation or {@code null} if there was no parent
         */
        @Nullable
        Observation getParentObservation();

        /**
         * Optional error that occurred while processing the {@link Observation}.
         * @return optional error
         */
        Optional<Throwable> getError();

        /**
         * Gets an entry from the context. Returns {@code null} when entry is not present.
         * @param key key
         * @param <T> value type
         * @return entry or {@code null} if not present
         */
        @Nullable
        <T> T get(Object key);

        /**
         * Gets an entry from the context. Throws exception when entry is not present.
         * @param key key
         * @param <T> value type
         * @return entry ot exception if not present
         */
        @NonNull
        <T> T getRequired(Object key);

        /**
         * Checks if context contains a key.
         * @param key key
         * @return {@code true} when the context contains the entry with the given key
         */
        boolean containsKey(Object key);

        /**
         * Returns an element or default if not present.
         * @param key key
         * @param defaultObject default object to return
         * @param <T> value type
         * @return object or default if not present
         */
        <T> T getOrDefault(Object key, T defaultObject);

        /**
         * Returns low cardinality key values
         * @return low cardinality key values
         */
        KeyValues getLowCardinalityKeyValues();

        /**
         * Returns high cardinality key values
         * @return high cardinality key values
         */
        @NonNull
        KeyValues getHighCardinalityKeyValues();

        /**
         * Returns all key values
         * @return all key values
         */
        @NonNull
        KeyValues getAllKeyValues();

    }

    /**
     * A marker interface for conventions of {@link KeyValues} naming.
     *
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface KeyValuesConvention {

    }

    /**
     * Contains conventions for naming and {@link KeyValues} providing.
     *
     * @param <T> type of context
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface ObservationConvention<T extends Observation.Context>
            extends Observation.KeyValuesProvider<T>, KeyValuesConvention {

        /**
         * Allows to override the name for an observation.
         * @return the new name for the observation
         */
        String getName();

    }

    /**
     * A provider of key values.
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
         * @return key values
         */
        default KeyValues getLowCardinalityKeyValues(T context) {
            return KeyValues.empty();
        }

        /**
         * High cardinality key values.
         * @return key values
         */
        default KeyValues getHighCardinalityKeyValues(T context) {
            return KeyValues.empty();
        }

        /**
         * Tells whether this key values provider should be applied for a given
         * {@link Context}.
         * @param context a {@link Context}
         * @return {@code true} when this key values provider should be used
         */
        boolean supportsContext(Context context);

        /**
         * Key values provider wrapping other key values providers.
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        class CompositeKeyValuesProvider implements KeyValuesProvider<Context> {

            private final List<KeyValuesProvider> keyValuesProviders;

            /**
             * Creates a new instance of {@code CompositeKeyValueProvider}.
             * @param keyValuesProviders the key values providers that are registered
             * under the composite
             */
            public CompositeKeyValuesProvider(KeyValuesProvider... keyValuesProviders) {
                this(Arrays.asList(keyValuesProviders));
            }

            /**
             * Creates a new instance of {@code CompositeKeyValueProvider}.
             * @param keyValuesProviders the key values providers that are registered
             * under the composite
             */
            public CompositeKeyValuesProvider(List<KeyValuesProvider> keyValuesProviders) {
                this.keyValuesProviders = keyValuesProviders;
            }

            @Override
            public KeyValues getLowCardinalityKeyValues(Context context) {
                return getProvidersForContext(context).map(provider -> provider.getLowCardinalityKeyValues(context))
                        .reduce(KeyValues::and).orElse(KeyValues.empty());
            }

            private Stream<KeyValuesProvider> getProvidersForContext(Context context) {
                return this.keyValuesProviders.stream().filter(provider -> provider.supportsContext(context));
            }

            @Override
            public KeyValues getHighCardinalityKeyValues(Context context) {
                return getProvidersForContext(context).map(provider -> provider.getHighCardinalityKeyValues(context))
                        .reduce(KeyValues::and).orElse(KeyValues.empty());
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
     * A provider of key values that will be set on the {@link ObservationRegistry}.
     *
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface GlobalKeyValuesProvider<T extends Context> extends KeyValuesProvider<T> {

    }

    /**
     * An observation convention that will be set on the {@link ObservationRegistry}.
     *
     * @author Marcin Grzejszczak
     * @since 1.10.0
     */
    interface GlobalObservationConvention<T extends Context> extends ObservationConvention<T> {

    }

    /**
     * A functional interface like {@link Runnable} but it can throw a {@link Throwable}.
     */
    @FunctionalInterface
    interface CheckedRunnable {

        void run() throws Throwable;

    }

    /**
     * A functional interface like {@link Callable} but it can throw a {@link Throwable}.
     */
    @FunctionalInterface
    interface CheckedCallable<T> {

        T call() throws Throwable;

    }

}
