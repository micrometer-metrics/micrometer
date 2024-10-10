/**
 * Copyright 2022 the original author or authors.
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
package io.micrometer.samples.spring6.aop;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueResolver;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.PARAMETER)
@interface HighCardinality {

    /**
     * The name of the key of the tag which should be created.
     * @return the tag key
     */
    String value() default "";

    /**
     * The name of the key of the tag which should be created.
     * @return the tag value
     */
    String key() default "";

    /**
     * Execute this expression to calculate the tag value. Will be analyzed if no value of
     * the {@link HighCardinality#resolver()} was set.
     * @return an expression
     */
    String expression() default "";

    /**
     * Use this bean to resolve the tag value. Has the highest precedence.
     * @return {@link ValueResolver} bean
     */
    Class<? extends ValueResolver> resolver() default NoOpValueResolver.class;

}
