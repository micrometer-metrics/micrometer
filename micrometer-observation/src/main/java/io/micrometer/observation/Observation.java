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
public interface Observation extends ObservationView {

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
            return NOOP;
        }
        return new SimpleObservation(name, registry, context == null ? new Context() : context);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation. Allows to set a custom {@link ObservationConvention} and requires to
     * provide a default one if neither a custom nor a pre-configured one (via
     * {@link ObservationRegistry.ObservationConfig#getObservationConvention(Context, ObservationConvention)})
     * was found. The {@link ObservationConvention} implementation can override
     * {@link Observation} names (i.e. name and contextual name) and key values.
     * @param <T> type of context
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked.
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a configured one was found
     * @param context the observation context
     * @param registry observation registry
     * @return created but not started observation
     */
    static <T extends Context> Observation createNotStarted(@Nullable ObservationConvention<T> customConvention,
            @NonNull ObservationConvention<T> defaultConvention, @NonNull T context,
            @NonNull ObservationRegistry registry) {
        ObservationConvention<T> convention;
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
    static Observation start(ObservationConvention<? extends Context> observationConvention,
            @Nullable ObservationRegistry registry) {
        return start(observationConvention, null, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * @param <T> type of context
     * @param observationConvention observation convention
     * @param context mutable context
     * @param registry observation registry
     * @return started observation
     */
    static <T extends Context> Observation start(ObservationConvention<T> observationConvention, @Nullable T context,
            @Nullable ObservationRegistry registry) {
        return createNotStarted(observationConvention, context, registry).start();
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation. Allows to set a
     * custom {@link ObservationConvention} and requires to provide a default one if
     * neither a custom nor a pre-configured one (via
     * {@link ObservationRegistry.ObservationConfig#getObservationConvention(Context, ObservationConvention)})
     * was found.
     * @param <T> type of context
     * @param registry observation registry
     * @param context the observation context
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked.
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a configured one was found
     * @return started observation
     */
    static <T extends Context> Observation start(@Nullable ObservationConvention<T> customConvention,
            @NonNull ObservationConvention<T> defaultConvention, @NonNull T context,
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
    static Observation createNotStarted(ObservationConvention<? extends Context> observationConvention,
            @Nullable ObservationRegistry registry) {
        return createNotStarted(observationConvention, null, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * <p>
     * <b>Important!</b> If you're using the
     * {@link ObservationConvention#getContextualName(Context)} method to override the
     * contextual name <b>you MUST use a non {@code null} context</b> (i.e. the
     * {@code context} parameter of this method MUST NOT be {@code null}). The
     * {@link ObservationConvention#getContextualName(Context)} requires a concrete type
     * of {@link Context} to be passed and if you're not providing one we won't be able to
     * initialize it ourselves.
     * </p>
     * @param <T> type of context
     * @param observationConvention observation convention
     * @param context mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static <T extends Context> Observation createNotStarted(ObservationConvention<T> observationConvention,
            @Nullable T context, @Nullable ObservationRegistry registry) {
        if (registry == null || registry.isNoop()
                || !registry.observationConfig().isObservationEnabled(observationConvention.getName(), context)
                || observationConvention == NoopObservationConvention.INSTANCE) {
            return NOOP;
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
    Observation contextualName(@Nullable String contextualName);

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
     * have an unbounded number of possible values. An HTTP URL is a good example of such
     * a key value (e.g. /foo/bar, /foo/baz etc.).
     * @param keyValue key value
     * @return this
     */
    Observation highCardinalityKeyValue(KeyValue keyValue);

    /**
     * Adds a high cardinality key value. High cardinality means that this key value will
     * have an unbounded number of possible values. An HTTP URL is a good example of such
     * a key value (e.g. /foo/bar, /foo/baz etc.).
     * @param key key
     * @param value value
     * @return this
     */
    default Observation highCardinalityKeyValue(String key, String value) {
        return highCardinalityKeyValue(KeyValue.of(key, value));
    }

    /**
     * Adds multiple high cardinality key value instances. High cardinality means that the
     * key value will have an unbounded number of possible values. An HTTP URL is a good
     * example of such a key value (e.g. /foo/bar, /foo/baz etc.).
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
        return this == NOOP;
    }

    /**
     * Adds an observation convention that can be used to attach key values to the
     * observation. WARNING: You must add ObservationConvention instances to the
     * Observation before it is started.
     * @param observationConvention observation convention
     * @return this
     */
    Observation observationConvention(ObservationConvention<?> observationConvention);

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
    Context getContext();

    /**
     * Returns the context attached to this observation as a read only view.
     * @return corresponding context
     */
    @Override
    default ContextView getContextView() {
        return getContext();
    }

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
        start();
        try (Scope scope = openScope()) {
            runnable.run();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
        finally {
            stop();
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
     * @param <E> type of exception thrown
     */
    @SuppressWarnings("unused")
    default <E extends Throwable> void observeChecked(CheckedRunnable<E> checkedRunnable) throws E {
        start();
        try (Scope scope = openScope()) {
            checkedRunnable.run();
        }
        catch (Throwable error) {
            error(error);
            throw error;
        }
        finally {
            stop();
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
        start();
        try (Scope scope = openScope()) {
            return supplier.get();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
        finally {
            stop();
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
     * @param <T> the return type of the {@link CheckedCallable}
     * @param <E> type of exception checkedCallable throws
     * @return the result from {@link CheckedCallable#call()}
     */
    @SuppressWarnings("unused")
    default <T, E extends Throwable> T observeChecked(CheckedCallable<T, E> checkedCallable) throws E {
        start();
        try (Scope scope = openScope()) {
            return checkedCallable.call();
        }
        catch (Throwable error) {
            error(error);
            throw error;
        }
        finally {
            stop();
        }
    }

    /**
     * Wraps the given action in a scope and signals an error.
     * @param runnable the {@link Runnable} to run
     */
    @SuppressWarnings("unused")
    default void scoped(Runnable runnable) {
        try (Scope scope = openScope()) {
            runnable.run();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
    }

    /**
     * Wraps the given action in a scope and signals an error.
     * @param checkedRunnable the {@link CheckedRunnable} to run
     * @param <E> type of exception thrown
     */
    @SuppressWarnings("unused")
    default <E extends Throwable> void scopedChecked(CheckedRunnable<E> checkedRunnable) throws E {
        try (Scope scope = openScope()) {
            checkedRunnable.run();
        }
        catch (Throwable throwable) {
            error(throwable);
            throw throwable;
        }
    }

    /**
     * Wraps the given action in a scope and signals an error.
     * @param supplier the {@link Supplier} to call
     * @param <T> the return type of the {@link Supplier}
     * @return the result from {@link Supplier#get()}
     */
    @SuppressWarnings("unused")
    default <T> T scoped(Supplier<T> supplier) {
        try (Scope scope = openScope()) {
            return supplier.get();
        }
        catch (Exception exception) {
            error(exception);
            throw exception;
        }
    }

    /**
     * Wraps the given action in a scope and signals an error.
     * @param checkedCallable the {@link CheckedCallable} to call
     * @param <T> the return type of the {@link CheckedCallable}
     * @param <E> type of exception checkedCallable throws
     * @return the result from {@link CheckedCallable#call()}
     */
    @SuppressWarnings("unused")
    default <T, E extends Throwable> T scopedChecked(CheckedCallable<T, E> checkedCallable) throws E {
        try (Scope scope = openScope()) {
            return checkedCallable.call();
        }
        catch (Throwable error) {
            error(error);
            throw error;
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
     * @param checkedRunnable the {@link CheckedRunnable} to run
     * @param <E> type of exception checkedRunnable throws
     */
    static <E extends Throwable> void tryScopedChecked(@Nullable Observation parent, CheckedRunnable<E> checkedRunnable)
            throws E {
        if (parent != null) {
            parent.scopedChecked(checkedRunnable);
        }
        else {
            checkedRunnable.run();
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
     * Tries to run the action against an Observation. If the Observation is null, we just
     * run the action, otherwise we run the action in scope.
     * @param parent observation, potentially {@code null}
     * @param checkedCallable the {@link CheckedCallable} to call
     * @param <T> the return type of the {@link CheckedCallable}
     * @param <E> type of exception checkedCallable throws
     * @return the result from {@link CheckedCallable#call()}
     */
    static <T, E extends Throwable> T tryScopedChecked(@Nullable Observation parent,
            CheckedCallable<T, E> checkedCallable) throws E {
        if (parent != null) {
            return parent.scopedChecked(checkedCallable);
        }
        return checkedCallable.call();
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
            return this == NOOP;
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
        private ObservationView parentObservation;

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
         */
        public void setName(String name) {
            this.name = name;
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
         */
        public void setContextualName(@Nullable String contextualName) {
            this.contextualName = contextualName;
        }

        /**
         * Returns the parent {@link ObservationView}.
         * @return parent observation or {@code null} if there was no parent
         */
        @Nullable
        public ObservationView getParentObservation() {
            return parentObservation;
        }

        /**
         * Sets the parent {@link Observation}.
         * @param parentObservation parent observation to set
         */
        public void setParentObservation(@Nullable ObservationView parentObservation) {
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
         */
        public void setError(Throwable error) {
            this.error = error;
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
         * @throws IllegalArgumentException if not present
         * @return entry
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
         * the {@link ObservationConvention#getLowCardinalityKeyValues(Context)} method.
         * @param keyValue a key value
         * @return this context
         */
        public Context addLowCardinalityKeyValue(KeyValue keyValue) {
            this.lowCardinalityKeyValues.add(keyValue);
            return this;
        }

        /**
         * Adds a high cardinality key value - those will be appended to those fetched
         * from the {@link ObservationConvention#getHighCardinalityKeyValues(Context)}
         * method.
         * @param keyValue a key value
         * @return this context
         */
        public Context addHighCardinalityKeyValue(KeyValue keyValue) {
            this.highCardinalityKeyValues.add(keyValue);
            return this;
        }

        /**
         * Adds multiple low cardinality key values at once.
         * @param keyValues collection of key values
         * @return this context
         */
        public Context addLowCardinalityKeyValues(KeyValues keyValues) {
            keyValues.stream().forEach(this::addLowCardinalityKeyValue);
            return this;
        }

        /**
         * Adds multiple high cardinality key values at once.
         * @param keyValues collection of key values
         * @return this context
         */
        public Context addHighCardinalityKeyValues(KeyValues keyValues) {
            keyValues.stream().forEach(this::addHighCardinalityKeyValue);
            return this;
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
            return getLowCardinalityKeyValues().and(getHighCardinalityKeyValues());
        }

        @Override
        public String toString() {
            return "name='" + name + '\'' + ", contextualName='" + contextualName + '\'' + ", error='" + error + '\''
                    + ", lowCardinalityKeyValues=" + toString(getLowCardinalityKeyValues())
                    + ", highCardinalityKeyValues=" + toString(getHighCardinalityKeyValues()) + ", map="
                    + toString(map);
        }

        private String toString(KeyValues keyValues) {
            return keyValues.stream().map(keyValue -> String.format("%s='%s'", keyValue.getKey(), keyValue.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        private String toString(Map<Object, Object> map) {
            return map.entrySet().stream().map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

    }

    /**
     * An arbitrary event that you can extend and signal during an {@link Observation}.
     * This helps you to tell to the {@link ObservationHandler} that something happened.
     * If you want to signal an exception/error, please use
     * {@link Observation#error(Throwable)} instead.
     */
    interface Event {

        /**
         * Creates an {@link Event} for the given names.
         * @param name The name of the event (should have low cardinality).
         * @param contextualName The contextual name of the event (can have high
         * cardinality).
         * @return event
         */
        static Event of(String name, String contextualName) {
            return new Event() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getContextualName() {
                    return contextualName;
                }

                @Override
                public String toString() {
                    return "event.name='" + getName() + "', event.contextualName='" + getContextualName() + '\'';
                }
            };
        }

        /**
         * Creates an {@link Event} for the given name.
         * @param name The name of the event (should have low cardinality).
         * @return event
         */
        static Event of(String name) {
            return of(name, name);
        }

        /**
         * Returns the name of the event.
         * @return the name of the event.
         */
        String getName();

        /**
         * Returns the contextual name of the event. You can use {@code %s} to represent
         * dynamic entries that should be resolved at runtime via
         * {@link String#format(String, Object...)}.
         * @return the contextual name of the event.
         */
        default String getContextualName() {
            return getName();
        }

        /**
         * Creates a new event with the given dynamic entries for the contextual name.
         * @param dynamicEntriesForContextualName variables to be resolved in
         * {@link Event#getContextualName()} via {@link String#format(String, Object...)}.
         * @return event
         */
        default Event format(Object... dynamicEntriesForContextualName) {
            return Event.of(getName(), String.format(getContextualName(), dynamicEntriesForContextualName));
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
         * Returns the parent {@link ObservationView}.
         * @return parent observation or {@code null} if there was no parent
         */
        @Nullable
        ObservationView getParentObservation();

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
         * @throws IllegalArgumentException if not present
         * @return entry
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
         * Returns low cardinality key values.
         * @return low cardinality key values
         */
        KeyValues getLowCardinalityKeyValues();

        /**
         * Returns high cardinality key values.
         * @return high cardinality key values
         */
        @NonNull
        KeyValues getHighCardinalityKeyValues();

        /**
         * Returns all key values.
         * @return all key values
         */
        @NonNull
        KeyValues getAllKeyValues();

    }

    /**
     * A functional interface like {@link Runnable} but it can throw a {@link Throwable}.
     */
    @FunctionalInterface
    interface CheckedRunnable<E extends Throwable> {

        void run() throws E;

    }

    /**
     * A functional interface like {@link Callable} but it can throw a {@link Throwable}.
     */
    @FunctionalInterface
    interface CheckedCallable<T, E extends Throwable> {

        T call() throws E;

    }

}
