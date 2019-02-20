/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.annotation;

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

    String[] extraTags() default {};

    boolean longTask() default false;

    double[] percentiles() default {};

    boolean histogram() default false;

    String description() default "";
}
