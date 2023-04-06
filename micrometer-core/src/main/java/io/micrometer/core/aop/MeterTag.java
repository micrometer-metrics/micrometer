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
package io.micrometer.core.aop;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;

import java.lang.annotation.*;

/**
 * There are 3 different ways to add tags to a meter. All of them are controlled by the
 * annotation values. Precedence is to first try with the {@link ValueResolver}. If the
 * value of the resolver wasn't set, try to evaluate an expression. If there’s no
 * expression just return a {@code toString()} value of the parameter.
 *
 * IMPORTANT: Provided tag values MUST BE of LOW-CARDINALITY. If you fail to provide
 * low-cardinality values, that can lead to performance issues of your metrics back end.
 * Values should not come from the end-user since those most likely be high-cardinality.
 *
 * @author Christian Schwerdtfeger
 * @author Marcin Grzejszczak
 * @since 1.11.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.PARAMETER)
public @interface MeterTag {

    /**
     * The name of the key of the tag which should be created. This is an alias for
     * {@link MeterTag#key()}.
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
     * the {@link MeterTag#resolver()} was set. You need to have a
     * {@link ValueExpressionResolver} registered on the {@link MeterAnnotationHandler} to
     * provide the expression resolution engine.
     * @return an expression
     */
    String expression() default "";

    /**
     * Use this object to resolve the tag value. Has the highest precedence.
     * @return {@link ValueResolver} class
     */
    Class<? extends ValueResolver> resolver() default NoOpValueResolver.class;

}
