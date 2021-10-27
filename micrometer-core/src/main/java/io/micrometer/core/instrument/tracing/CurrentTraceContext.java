/*
 * Copyright 2013-2021 the original author or authors.
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

package io.micrometer.core.instrument.tracing;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.micrometer.core.lang.Nullable;

/**
 * This API was heavily influenced by Brave. Parts of its documentation were taken
 * directly from Brave.
 *
 * This makes a given span the current span by placing it in scope (usually but not always
 * a thread local scope).
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface CurrentTraceContext {

	/**
	 * Returns the current {@link TraceContext}.
	 *
	 * @return current {@link TraceContext} or {@code null} if not set
	 */
	@Nullable
	TraceContext context();

	/**
	 * Sets the current span in scope until the returned object is closed. It is a
	 * programming error to drop or never close the result. Using try-with-resources is
	 * preferred for this reason.
	 *
	 * @param context span to place into scope or {@code null} to clear the scope
	 * @return the scope with the span set
	 */
	Scope newScope(@Nullable TraceContext context);

	/**
	 * Like {@link #newScope(TraceContext)}, except returns a noop scope if the given
	 * context is already in scope.
	 *
	 * @param context span to place into scope or {@code null} to clear the scope
	 * @return the scope with the span set
	 */
	Scope maybeScope(@Nullable TraceContext context);

	/**
	 * Wraps a task in a trace representation.
	 *
	 * @param task task to wrap
	 * @param <C> task return type
	 * @return wrapped task
	 */
	<C> Callable<C> wrap(Callable<C> task);

	/**
	 * Wraps a task in a trace representation.
	 *
	 * @param task task to wrap
	 * @return wrapped task
	 */
	Runnable wrap(Runnable task);

	/**
	 * Wraps an executor in a trace representation.
	 *
	 * @param delegate executor to wrap
	 * @return wrapped executor
	 */
	Executor wrap(Executor delegate);

	/**
	 * Wraps an executor service in a trace representation.
	 *
	 * @param delegate executor service to wrap
	 * @return wrapped executor service
	 */
	ExecutorService wrap(ExecutorService delegate);

	/**
	 * Scope of a span. Needs to be closed so that resources are let go (e.g. MDC is
	 * cleared).
	 */
	interface Scope extends Closeable {

		@Override
		void close();

	}

}
