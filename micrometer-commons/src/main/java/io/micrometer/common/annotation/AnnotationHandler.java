/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.common.annotation;

import io.micrometer.common.KeyValue;

import java.lang.annotation.Annotation;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class is able to find all methods annotated with the Micrometer annotations. All
 * methods mean that if you have both an interface and an implementation annotated with
 * Micrometer annotations then this class is capable of finding both of them and merging
 * into one set of information.
 * <p>
 * This information is then used to add proper key-values to objects such as span or timer
 * from the method arguments that are annotated with a proper annotation.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @param <T> type which should be enriched with {@link KeyValue} information
 * @author Christian Schwerdtfeger
 * @since 1.11.0
 */
public class AnnotationHandler<T> extends GenericAnnotationHandler<KeyValue, T> {

    /**
     * Creates a new instance of {@link AnnotationHandler}.
     * @param keyValueConsumer consumer that takes a {@link KeyValue} and mutates the
     * {@code <T>} type
     * @param resolverProvider function converting a class extending a
     * {@link ValueResolver} to an instance of that class
     * @param expressionResolverProvider function converting a class extending a
     * {@link ValueExpressionResolver} to an instance of that class
     * @param annotation annotation containing {@link KeyValue} related information
     * @param toKeyValue function converting the annotation and the expression or
     * annotation value to a {@link KeyValue}
     */
    public AnnotationHandler(BiConsumer<KeyValue, T> keyValueConsumer,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider,
            Class<? extends Annotation> annotation, BiFunction<Annotation, Object, KeyValue> toKeyValue) {
        super(keyValueConsumer, resolverProvider, expressionResolverProvider, annotation, toKeyValue);
    }

}
