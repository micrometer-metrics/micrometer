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
package io.micrometer.observation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micrometer.observation.Observation;

/**
 * Annotation to mark classes and methods that you want to observe.
 *
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Observed {

    /**
     * Name of the {@link Observation}.
     * @return name of the {@link Observation}
     */
    String name() default "";

    /**
     * Contextual name of the {@link Observation}.
     * @return contextual name of the {@link Observation}
     */
    String contextualName() default "";

    /**
     * Low cardinality key values.
     * @return an array of low cardinality key values.
     */
    String[] lowCardinalityKeyValues() default {};

}
