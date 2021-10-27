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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide the name for the span. You should annotate all your custom
 * {@link Runnable Runnable} or {@link java.util.concurrent.Callable Callable} classes for
 * the instrumentation logic to pick up how to name the span.
 * <p>
 *
 * Having for example the following code <pre>{@code
 *     &#64;SpanName("custom-operation")
 *     class CustomRunnable implements Runnable {
 *         &#64;Override
 *         public void run() {
 *          // latency of this method will be recorded in a span named "custom-operation"
 *         }
 *      }
 * }</pre>
 *
 * Will result in creating a span with name {@code custom-operation}.
 * <p>
 *
 * When there's no @SpanName annotation, {@code toString} is used. Here's an example of
 * the above, but via an anonymous instance. <pre>{@code
 *     return new Runnable() {
 *          -- snip --
 *
 *          &#64;Override
 *          public String toString() {
 *              return "custom-operation";
 *          }
 *     };
 * }</pre>
 *
 * Starting with version {@code 1.3.0} you can also put the annotation on an
 * {@code @Async} annotated method and the value of that annotation will be used as the
 * span name.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpanName {

	/**
	 * Name of the span to be resolved at runtime.
	 *
	 * @return value of the span name
	 */
	String value();

}
