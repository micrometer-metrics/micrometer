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
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
 * @author Yanming Zhou
 * @since 1.10.0
 */
public interface Observation extends ObservationView {

    /**
     * No-op observation.
     */
    Observation NOOP = new NoopObservation();

    /**
     * Create and start an {@link Observation} with the given name. All Observations of
     * the same type must share the same name.
     * <p>
     * When no registry is passed or the observation is
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * not applicable}, a no-op observation will be returned.
     * @param name name of the observation
     * @param registry observation registry
     * @return a started observation
     */
    static Observation start(String name, @Nullable ObservationRegistry registry) {
        return start(name, Context::new, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When the {@link ObservationRegistry} is
     * null or the no-op registry, this fast returns a no-op {@link Observation} and skips
     * the creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled
     * (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned.
     * @param name name of the observation
     * @param contextSupplier mutable context supplier
     * @param registry observation registry
     * @return started observation
     */
    static <T extends Context> Observation start(String name, Supplier<T> contextSupplier,
            @Nullable ObservationRegistry registry) {
        return createNotStarted(name, contextSupplier, registry).start();
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
        return createNotStarted(name, Context::new, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When the
     * {@link ObservationRegistry} is null or the no-op registry, this fast returns a
     * no-op {@link Observation} and skips the creation of the
     * {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled
     * (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned.
     * @param name name of the observation
     * @param contextSupplier supplier for mutable context
     * @param registry observation registry
     * @return created but not started observation
     */
    static <T extends Context> Observation createNotStarted(String name, Supplier<T> contextSupplier,
            @Nullable ObservationRegistry registry) {
        if (registry == null || registry.isNoop()) {
            return NOOP;
        }
        Context context = contextSupplier.get();
        context.setParentFromCurrentObservation(registry);
        if (!registry.observationConfig().isObservationEnabled(name, context)) {
            return NOOP;
        }
        return new SimpleObservation(name, registry, context);
    }

    // @formatter:off
    /**
     * Creates but <b>does not start</b> an {@link Observation}.
     * <p>Remember to call {@link Observation#start()} when you want the measurements to start.
     * When the {@link ObservationRegistry} is null or the no-op registry, this returns a
     * no-op {@link Observation} and skips the creation of the {@link Observation.Context}.
     * If the observation is not enabled (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned.
     * <p>A single {@link ObservationConvention convention} will be used for this observation
     * for getting its name and {@link KeyValues key values}:
     * <ol>
     *  <li>the {@code customConvention} given as an argument, if not {@code null}
     *  <li>a {@link GlobalObservationConvention} configured on the
     *  {@link ObservationRegistry.ObservationConfig#observationConvention(GlobalObservationConvention)}
     *  that matches this observation
     *  <li>as a fallback, the {@code defaultConvention} will be used if none of the above are available
     * </ol>
     * @param <T> type of context
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked.
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a pre-configured one was found
     * @param contextSupplier supplier for the observation context
     * @param registry observation registry
     * @return created but not started observation
     */
    // @formatter:on
    static <T extends Context> Observation createNotStarted(@Nullable ObservationConvention<T> customConvention,
            ObservationConvention<T> defaultConvention, Supplier<T> contextSupplier,
            @Nullable ObservationRegistry registry) {
        if (registry == null || registry.isNoop()) {
            return Observation.NOOP;
        }
        ObservationConvention<T> convention;
        T context = contextSupplier.get();
        context.setParentFromCurrentObservation(registry);
        if (customConvention != null) {
            convention = customConvention;
        }
        else {
            convention = registry.observationConfig().getObservationConvention(context, defaultConvention);
        }
        if (!registry.observationConfig().isObservationEnabled(convention.getName(), context)) {
            return NOOP;
        }
        return new SimpleObservation(convention, registry, context);
    }

    /**
     * Creates and starts an {@link Observation}. When no registry is passed or
     * observation is not applicable will return a no-op observation.
     * <p>
     * Please check the javadoc of
     * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
     * method for the logic of choosing the convention.
     * </p>
     * @param observationConvention observation convention
     * @param registry observation registry
     * @return started observation
     */
    static Observation start(ObservationConvention<Context> observationConvention, ObservationRegistry registry) {
        return start(observationConvention, Context::new, registry);
    }

    /**
     * Creates and starts an {@link Observation}. When the {@link ObservationRegistry} is
     * null or the no-op registry, this fast returns a no-op {@link Observation} and skips
     * the creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled
     * (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned.
     * <p>
     * Please check the javadoc of
     * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
     * method for the logic of choosing the convention.
     * </p>
     * @param <T> type of context
     * @param observationConvention observation convention
     * @param contextSupplier mutable context supplier
     * @param registry observation registry
     * @return started observation
     */
    static <T extends Context> Observation start(ObservationConvention<T> observationConvention,
            Supplier<T> contextSupplier, ObservationRegistry registry) {
        return createNotStarted(observationConvention, contextSupplier, registry).start();
    }

    /**
     * Creates and starts an {@link Observation}. When the {@link ObservationRegistry} is
     * null or the no-op registry, this fast returns a no-op {@link Observation} and skips
     * the creation of the {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled
     * (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned. Allows to set a custom {@link ObservationConvention} and requires to
     * provide a default one if neither a custom nor a pre-configured one (via
     * {@link ObservationRegistry.ObservationConfig#getObservationConvention(Context, ObservationConvention)})
     * was found.
     * <p>
     * Please check the javadoc of
     * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
     * method for the logic of choosing the convention.
     * </p>
     * @param <T> type of context
     * @param registry observation registry
     * @param contextSupplier the observation context supplier
     * @param customConvention custom convention. If {@code null}, the default one will be
     * picked.
     * @param defaultConvention default convention when no custom convention was passed,
     * nor a configured one was found
     * @return started observation
     */
    static <T extends Context> Observation start(@Nullable ObservationConvention<T> customConvention,
            ObservationConvention<T> defaultConvention, Supplier<T> contextSupplier, ObservationRegistry registry) {
        return createNotStarted(customConvention, defaultConvention, contextSupplier, registry).start();
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When no
     * registry is passed or observation is not applicable will return a no-op
     * observation.
     * <p>
     * Please check the javadoc of
     * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
     * method for the logic of choosing the convention.
     * </p>
     * @param observationConvention observation convention
     * @param registry observation registry
     * @return created but not started observation
     */
    static Observation createNotStarted(ObservationConvention<Context> observationConvention,
            ObservationRegistry registry) {
        return createNotStarted(observationConvention, Context::new, registry);
    }

    /**
     * Creates but <b>does not start</b> an {@link Observation}. Remember to call
     * {@link Observation#start()} when you want the measurements to start. When the
     * {@link ObservationRegistry} is null or the no-op registry, this fast returns a
     * no-op {@link Observation} and skips the creation of the
     * {@link Observation.Context}. This check avoids unnecessary
     * {@link Observation.Context} creation, which is why it takes a {@link Supplier} for
     * the context rather than the context directly. If the observation is not enabled
     * (see
     * {@link ObservationRegistry.ObservationConfig#observationPredicate(ObservationPredicate)
     * ObservationConfig#observationPredicate}), a no-op observation will also be
     * returned.
     * <p>
     * <b>Important!</b> If you're using the
     * {@link ObservationConvention#getContextualName(Context)} method to override the
     * contextual name <b>you MUST use a non {@code null} context</b> (i.e. the
     * {@code context} parameter of this method MUST NOT be {@code null}). The
     * {@link ObservationConvention#getContextualName(Context)} requires a concrete type
     * of {@link Context} to be passed and if you're not providing one we won't be able to
     * initialize it ourselves.
     * </p>
     * <p>
     * Please check the javadoc of
     * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
     * method for the logic of choosing the convention.
     * </p>
     * @param <T> type of context
     * @param observationConvention observation convention
     * @param contextSupplier mutable context supplier
     * @param registry observation registry
     * @return created but not started observation
     */
    static <T extends Context> Observation createNotStarted(ObservationConvention<T> observationConvention,
            Supplier<T> contextSupplier, ObservationRegistry registry) {
        if (registry == null || registry.isNoop() || observationConvention == NoopObservationConvention.INSTANCE) {
            return NOOP;
        }
        T context = contextSupplier.get();
        context.setParentFromCurrentObservation(registry);
        if (!registry.observationConfig().isObservationEnabled(observationConvention.getName(), context)) {
            return NOOP;
        }
        return new SimpleObservation(observationConvention, registry, context);
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
    Observation parentObservation(@Nullable Observation parentObservation);

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
        for (KeyValue keyValue : keyValues) {
            lowCardinalityKeyValue(keyValue);
        }
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
        for (KeyValue keyValue : keyValues) {
            highCardinalityKeyValue(keyValue);
        }
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
     * Sets an observation convention that can be used to attach key values to the
     * observation. Implementations should only set the convention when it supports (see:
     * {@link ObservationConvention#supportsContext(Context)}) the current
     * {@link Context}. WARNING: You must set the ObservationConvention to the Observation
     * before it is started.
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
    default void observe(Runnable runnable) {
        start();
        try (Scope scope = openScope()) {
            runnable.run();
        }
        catch (Throwable error) {
            error(error);
            throw error;
        }
        finally {
            stop();
        }
    }

    default Runnable wrap(Runnable runnable) {
        return () -> observe(runnable);
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

    default <E extends Throwable> CheckedRunnable<E> wrapChecked(CheckedRunnable<E> checkedRunnable) throws E {
        return () -> observeChecked(checkedRunnable);
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
    @Nullable
    default <T> T observe(Supplier<T> supplier) {
        start();
        try (Scope scope = openScope()) {
            return supplier.get();
        }
        catch (Throwable error) {
            error(error);
            throw error;
        }
        finally {
            stop();
        }
    }

    default <T> Supplier<T> wrap(Supplier<T> supplier) {
        return () -> observe(supplier);
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
    @Nullable
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

    default <T, E extends Throwable> CheckedCallable<T, E> wrapChecked(CheckedCallable<T, E> checkedCallable) throws E {
        return () -> observeChecked(checkedCallable);
    }

    /**
     * Observes the passed {@link Function} which provides access to the {@link Context}.
     *
     * This means the followings:
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link Function#apply(Object)} where it gets a {@link Context}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * NOTE: When the {@link ObservationRegistry} is a noop, this function receives a
     * default {@link Context} instance which is not the one that has been passed at
     * {@link Observation} creation.
     * @param function the {@link Function} to call
     * @return the result from {@link Function#apply(Object)}
     * @param <C> the type of input {@link Context} to the function
     * @param <T> the type parameter of the {@link Function} return
     * @since 1.11.0
     * @deprecated scheduled for removal in 1.15.0, use {@code observe(...)} directly
     */
    @SuppressWarnings({ "unused", "unchecked" })
    @Nullable
    @Deprecated
    default <C extends Context, T> T observeWithContext(Function<C, T> function) {
        InternalLoggerFactory.getInstance(Observation.class)
            .warn("This method is deprecated. Please migrate to observation.observe(...)");
        start();
        try (Scope scope = openScope()) {
            return function.apply((C) getContext());
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
     * Observes the passed {@link Function} which provides access to the {@link Context}.
     *
     * This means the followings:
     * <ul>
     * <li>Starts the {@code Observation}</li>
     * <li>Opens a {@code Scope}</li>
     * <li>Calls {@link Function#apply(Object)} where it gets a {@link Context}</li>
     * <li>Closes the {@code Scope}</li>
     * <li>Signals the error to the {@code Observation} if any</li>
     * <li>Stops the {@code Observation}</li>
     * </ul>
     *
     * NOTE: When the {@link ObservationRegistry} is a noop, this function receives a
     * default {@link Context} instance which is not the one that has been passed at
     * {@link Observation} creation.
     * @param function the {@link CheckedFunction} to call
     * @return the result from {@link Function#apply(Object)}
     * @param <C> the type of input {@link Context} to the function
     * @param <T> the type of return to the function
     * @param <E> type of exception {@link CheckedFunction} throws
     * @since 1.11.0
     * @deprecated scheduled for removal in 1.15.0, use {@code observeChecked(...)}
     * directly
     */
    @SuppressWarnings({ "unused", "unchecked" })
    @Nullable
    @Deprecated
    default <C extends Context, T, E extends Throwable> T observeCheckedWithContext(CheckedFunction<C, T, E> function)
            throws E {
        InternalLoggerFactory.getInstance(Observation.class)
            .warn("This method is deprecated. Please migrate to observation.observeChecked(...)");
        start();
        try (Scope scope = openScope()) {
            return function.apply((C) getContext());
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

        /**
         * Parent scope.
         * @return previously opened scope when this one was created
         * @since 1.10.8
         */
        @Nullable
        default Observation.Scope getPreviousObservationScope() {
            return null;
        }

        /**
         * Clears the current scope and notifies the handlers that the scope was closed.
         * You don't need to call this method manually. If you use try-with-resource, it
         * will call this for you. Please only call this method if you know what you are
         * doing and your use-case demands the usage of it.
         */
        @Override
        void close();

        /**
         * Resets the current scope. The effect of calling this method should be clearing
         * all related thread local entries.
         *
         * You don't need to call this method in most of the cases. Please only call this
         * method if you know what you are doing and your use-case demands the usage of
         * it.
         * @since 1.10.4
         */
        void reset();

        /**
         * This method assumes that all previous scopes got {@link #reset()}. That means
         * that in thread locals there are no more entries, and now we can make this scope
         * current.
         *
         * Making this scope current can lead to additional work such as injecting
         * variables to MDC.
         *
         * You don't need to call this method in most of the cases. Please only call this
         * method if you know what you are doing and your use-case demands the usage of
         * it.
         * @since 1.10.6
         */
        void makeCurrent();

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

        private final Map<Object, Object> map = new ConcurrentHashMap<>();

        private String name;

        @Nullable
        private String contextualName;

        @Nullable
        private Throwable error;

        @Nullable
        private ObservationView parentObservation;

        private final Map<String, KeyValue> lowCardinalityKeyValues = new ConcurrentHashMap<>();

        private final Map<String, KeyValue> highCardinalityKeyValues = new ConcurrentHashMap<>();

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
         * Sets the parent {@link ObservationView}.
         * @param parentObservation parent observation to set
         */
        public void setParentObservation(@Nullable ObservationView parentObservation) {
            this.parentObservation = parentObservation;
        }

        /**
         * Sets the parent {@link ObservationView} to current one if parent is null and
         * current one exists.
         * @param registry the {@link ObservationRegistry} in using
         */
        void setParentFromCurrentObservation(ObservationRegistry registry) {
            if (this.parentObservation == null) {
                Observation currentObservation = registry.getCurrentObservation();
                if (currentObservation != null) {
                    setParentObservation(currentObservation);
                }
            }
        }

        /**
         * Error that occurred while processing the {@link Observation}.
         * @return error (null if there wasn't any)
         */
        @Nullable
        @Override
        public Throwable getError() {
            return this.error;
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
            this.lowCardinalityKeyValues.put(keyValue.getKey(), keyValue);
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
            this.highCardinalityKeyValues.put(keyValue.getKey(), keyValue);
            return this;
        }

        /**
         * Removes a low cardinality key value by looking at its key - those will be
         * removed to those fetched from the
         * {@link ObservationConvention#getLowCardinalityKeyValues(Context)} method.
         * @param keyName name of the key
         * @return this context
         * @since 1.10.1
         */
        public Context removeLowCardinalityKeyValue(String keyName) {
            this.lowCardinalityKeyValues.remove(keyName);
            return this;
        }

        /**
         * Removes a high cardinality key value by looking at its key - those will be
         * removed to those fetched from the
         * {@link ObservationConvention#getHighCardinalityKeyValues(Context)} method.
         * @param keyName name of the key
         * @return this context
         * @since 1.10.1
         */
        public Context removeHighCardinalityKeyValue(String keyName) {
            this.highCardinalityKeyValues.remove(keyName);
            return this;
        }

        /**
         * Adds multiple low cardinality key values at once.
         * @param keyValues collection of key values
         * @return this context
         */
        public Context addLowCardinalityKeyValues(KeyValues keyValues) {
            for (KeyValue keyValue : keyValues) {
                addLowCardinalityKeyValue(keyValue);
            }
            return this;
        }

        /**
         * Adds multiple high cardinality key values at once.
         * @param keyValues collection of key values
         * @return this context
         */
        public Context addHighCardinalityKeyValues(KeyValues keyValues) {
            for (KeyValue keyValue : keyValues) {
                addHighCardinalityKeyValue(keyValue);
            }
            return this;
        }

        /**
         * Removes multiple low cardinality key values at once.
         * @param keyNames collection of key names
         * @return this context
         * @since 1.10.1
         */
        public Context removeLowCardinalityKeyValues(String... keyNames) {
            for (String keyName : keyNames) {
                removeLowCardinalityKeyValue(keyName);
            }
            return this;
        }

        /**
         * Removes multiple high cardinality key values at once.
         * @param keyNames collection of key names
         * @return this context
         * @since 1.10.1
         */
        public Context removeHighCardinalityKeyValues(String... keyNames) {
            for (String keyName : keyNames) {
                removeHighCardinalityKeyValue(keyName);
            }
            return this;
        }

        @NonNull
        @Override
        public KeyValues getLowCardinalityKeyValues() {
            return KeyValues.of(this.lowCardinalityKeyValues.values());
        }

        @NonNull
        @Override
        public KeyValues getHighCardinalityKeyValues() {
            return KeyValues.of(this.highCardinalityKeyValues.values());
        }

        @Override
        public KeyValue getLowCardinalityKeyValue(String key) {
            return this.lowCardinalityKeyValues.get(key);
        }

        @Override
        public KeyValue getHighCardinalityKeyValue(String key) {
            return this.highCardinalityKeyValues.get(key);
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
                    + ", highCardinalityKeyValues=" + toString(getHighCardinalityKeyValues()) + ", map=" + toString(map)
                    + ", parentObservation=" + parentObservation;
        }

        private String toString(KeyValues keyValues) {
            return keyValues.stream()
                .map(keyValue -> String.format("%s='%s'", keyValue.getKey(), keyValue.getValue()))
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private String toString(Map<Object, Object> map) {
            return map.entrySet()
                .stream()
                .map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
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
            return new SimpleEvent(name, contextualName);
        }

        /**
         * Creates an {@link Event} for the given names and timestamp (wall time).
         * @param name The name of the event (should have low cardinality).
         * @param contextualName The contextual name of the event (can have high
         * cardinality).
         * @param wallTime Wall time in milliseconds since the epoch
         * @return event
         * @since 1.12.0
         */
        static Event of(String name, String contextualName, long wallTime) {
            return new SimpleEvent(name, contextualName, wallTime);
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
         * Wall time in milliseconds since the epoch. Typically equivalent to
         * {@link System#currentTimeMillis()}. Should not be used to determine durations.
         * Used for timestamping events that happened during Observations.
         * @return Wall time in milliseconds since the epoch
         * @since 1.12.0
         */
        default long getWallTime() {
            return 0;
        }

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
         * Error that occurred while processing the {@link Observation}.
         * @return error (null if there wasn't any)
         */
        @Nullable
        Throwable getError();

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
         * Returns an element or default if not present.
         * @param key key
         * @param defaultObjectSupplier supplier for default object to return
         * @param <T> value type
         * @return object or default if not present
         * @since 1.11.0
         */
        default <T> T getOrDefault(Object key, Supplier<T> defaultObjectSupplier) {
            T value = get(key);
            return value != null ? value : defaultObjectSupplier.get();
        }

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
         * Returns a low cardinality key value or {@code null} if not present.
         * @param key key
         * @return a low cardinality key value or {@code null}
         */
        @Nullable
        KeyValue getLowCardinalityKeyValue(String key);

        /**
         * Returns a high cardinality key value or {@code null} if not present.
         * @param key key
         * @return a high cardinality key value or {@code null}
         */
        @Nullable
        KeyValue getHighCardinalityKeyValue(String key);

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

    /**
     * A functional interface like {@link Function} but it can throw a {@link Throwable}.
     *
     * @since 1.11.0
     */
    @FunctionalInterface
    interface CheckedFunction<T, R, E extends Throwable> {

        @Nullable
        R apply(T t) throws E;

    }

}
