/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.annotation;

import java.lang.annotation.*;

/**
 * Annotated methods would expose a few counter metrics about their execution status. By
 * default, both failed and successful attempts would be reported. Switching the
 * {@link #recordFailuresOnly()} to {@code true} would instruct the corresponding aspect
 * to only record the failed attempts.
 *
 * <p>
 * When the annotated method returns a {@link java.util.concurrent.CompletionStage} or any
 * of its subclasses, the counters will be incremented only when the
 * {@link java.util.concurrent.CompletionStage} is completed. If completed exceptionally a
 * failure is recorded, otherwise if {@link Counted#recordFailuresOnly()} is set to
 * {@code false}, a success is recorded.
 *
 * @author Ali Dehghani
 * @since 1.2.0
 * @see io.micrometer.core.aop.CountedAspect
 */
@Inherited
@Target({ ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {

    /**
     * Represents the metric name for the to-be-recorded counters.
     * @return The metric name.
     */
    String value() default "method.counted";

    /**
     * By default, both failed and successful attempts are recorded. Switch it to
     * {@code true} in order to only record failed attempts.
     * @return Whether to only report failures.
     */
    boolean recordFailuresOnly() default false;

    /**
     * List of key-value pair arguments to supply the Counter as extra tags.
     * @return key-value pair of tags
     * @see io.micrometer.core.instrument.Counter.Builder#tags(String...)
     * @since 1.4.0
     */
    String[] extraTags() default {};

    /**
     * An optional description for what the underlying counter is going to record.
     * @return The counter description.
     */
    String description() default "";

}
