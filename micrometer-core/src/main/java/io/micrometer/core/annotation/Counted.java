/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.annotation;

import java.lang.annotation.*;

/**
 * Annotated methods would expose a few counter metrics about their execution status.
 * By default, both failed and successful attempts would be reported. Switching the
 * {@link #successfulAttempts()} to {@code false} would instruct the corresponding aspect
 * to only record the failed attempts.
 *
 * @author Ali Dehghani
 * @see io.micrometer.core.aop.CountedAspect
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {

    /**
     * Represents the metric name for the to-be-recorded counters. The default value is
     * {@code method.counted}.
     *
     * @return The metric name.
     */
    String value() default "";

    /**
     * By default, both failed and successful attempts are recorded. Switch it to {@code false} in
     * order to only record failed attempts.
     *
     * @return Whether to report successful attempts or not.
     */
    boolean successfulAttempts() default true;

    /**
     * An optional description for what the underlying counter is going to record.
     *
     * @return The counter description.
     */
    String description() default "";
}
