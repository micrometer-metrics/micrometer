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

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents a {@link Span} stored in thread local.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class ThreadLocalSpan {

    private static final Log log = LogFactory.getLog(ThreadLocalSpan.class);

	private final ThreadLocal<SpanAndScope> threadLocalSpan = new ThreadLocal<>();

	private final Deque<SpanAndScope> spans = new LinkedBlockingDeque<>();

	private final Tracer tracer;

	/**
	 * Creates a new instance of {@link ThreadLocalSpan}.
	 *
	 * @param tracer tracer
	 */
	public ThreadLocalSpan(Tracer tracer) {
		this.tracer = tracer;
	}

	/**
	 * Creates a new span and sets it in scope.
	 *
	 * @return new thread local span
	 */
	public Span nextSpan() {
		Span span = this.tracer.nextSpan();
		set(span);
		return span;
	}

	/**
	 * Sets given span and scope.
	 *
	 * @param span span to be put in scope
	 */
	public void set(Span span) {
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span);
		SpanAndScope newSpanAndScope = new SpanAndScope(span, spanInScope);
		SpanAndScope scope = this.threadLocalSpan.get();
		if (scope != null) {
            if (log.isTraceEnabled()) {
                log.trace("Putting previous scope to stack [" + scope + "]");
            }
			this.spans.addFirst(scope);
		}
		this.threadLocalSpan.set(newSpanAndScope);
	}

	/**
	 * Returns the current span and scope.
	 *
	 * @return currently stored span and scope
	 */
	public SpanAndScope get() {
		return this.threadLocalSpan.get();
	}

	/**
	 * Removes the current span from thread local and brings back the previous span to the
	 * current thread local.
	 */
	public void remove() {
		this.threadLocalSpan.remove();
		if (this.spans.isEmpty()) {
			return;
		}
		try {
			SpanAndScope span = this.spans.removeFirst();
            if (log.isDebugEnabled()) {
                log.debug("Took span [" + span + "] from thread local");
            }
			this.threadLocalSpan.set(span);
		}
		catch (NoSuchElementException ex) {
            if (log.isTraceEnabled()) {
                log.trace("Failed to remove a span from the queue", ex);
            }
		}
	}

	/**
	 * Ends the current span and puts the previous one as the current span if present.
	 */
	public void end() {
		SpanAndScope spanAndScope = get();
		if (spanAndScope == null) {
			return;
		}
		spanAndScope.close();
		remove();
	}

}
