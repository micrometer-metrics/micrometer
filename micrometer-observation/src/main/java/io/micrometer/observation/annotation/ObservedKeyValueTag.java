/**
 * Copyright 2025 the original author or authors.
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
package io.micrometer.observation.annotation;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.aop.ObservedKeyValueTagAnnotationHandler;

import java.lang.annotation.*;

/**
 * There are 3 different ways to add tags to an observation. All of them are controlled by
 * the annotation values. Precedence is to first try with the {@link ValueResolver}. If
 * the value of the resolver wasn't set, try to evaluate an expression. If there’s no
 * expression just return a {@code toString()} value of the parameter. Cardinality type
 * also can be set by {@link CardinalityType}. default value is
 * {@link CardinalityType#LOW}.
 *
 * @author Seungyong Hong
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ ElementType.PARAMETER })
@Repeatable(ObservedKeyValueTags.class)
public @interface ObservedKeyValueTag {

    /**
     * The name of the key of the tag which should be created. This is an alias for
     * {@link #key()}.
     * @return the tag key name
     */
    String value() default "";

    /**
     * The name of the key of the tag which should be created.
     * @return the tag key name
     */
    String key() default "";

    /**
     * Execute this expression to calculate the tag value. Will be evaluated if no value
     * of the {@link #resolver()} was set. You need to have a
     * {@link ValueExpressionResolver} registered on the
     * {@link ObservedKeyValueTagAnnotationHandler} to provide the expression resolution
     * engine.
     * @return an expression
     */
    String expression() default "";

    /**
     * Use this object to resolve the tag value. Has the highest precedence.
     * @return {@link ValueResolver} class
     */
    Class<? extends ValueResolver> resolver() default NoOpValueResolver.class;

    /**
     * Cardinality type of the tag.
     * @return {@link CardinalityType} class
     */
    CardinalityType cardinality() default CardinalityType.LOW;

    /**
     * There are 2 types of cardinality in observation. And each type is treated
     * differently.
     */
    enum CardinalityType {

        LOW, HIGH

    }

}
