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

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Timer;

import java.lang.annotation.*;

@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Repeatable(TimedSet.class)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Timed {
    /**
     * Name of the Timer metric.
     *
     * @return name of the Timer metric
     */
    String value() default "";

    /**
     * List of key-value pair arguments to supply the Timer as extra tags.
     *
     * @return key-value pair of tags
     * @see Timer.Builder#tags(String...)
     */
    String[] extraTags() default {};

    /**
     * Flag of whether the Timer should be a {@link LongTaskTimer}.
     *
     * @return whether the timer is a LongTaskTimer
     */
    boolean longTask() default false;

    /**
     * List of percentiles to calculate client-side for the {@link Timer}.
     * For example, the 95th percentile should be passed as {@code 0.95}.
     * 
     * @return percentiles to calculate
     * @see Timer.Builder#publishPercentiles(double...)
     */
    double[] percentiles() default {};

    /**
     * Whether to enable recording of a percentile histogram for the {@link Timer Timer}.
     * 
     * @return whether percentile histogram is enabled
     * @see Timer.Builder#publishPercentileHistogram(Boolean)
     */
    boolean histogram() default false;

    /**
     * Description of the {@link Timer}.
     *
     * @return meter description
     * @see Timer.Builder#description(String)
     */
    String description() default "";
}
