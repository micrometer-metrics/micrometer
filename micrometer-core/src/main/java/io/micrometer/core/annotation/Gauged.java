/*
 * Copyright 2025 VMware, Inc.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated methods are instrumented using a {@code Gauge}
 *
 * @author Sandeep Vishnu
 * @see io.micrometer.core.aop.GaugedAspect
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Gauged {

    /**
     * Name of the gauge metric.
     * @return gauge name
     */
    String value() default "";

    /**
     * Description of the gauge.
     * @return gauge description
     */
    String description() default "";

    /**
     * Additional tags for the gauge.
     * @return array of tag strings in the form key:value
     */
    String[] extraTags() default {};

}
