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
package io.micrometer.observation.aop;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.aspectj.lang.ProceedingJoinPoint;
import org.jspecify.annotations.Nullable;
import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservationKeyValue;

/**
 * Annotation handler for {@link ObservationKeyValue}. To add support for
 * {@link ObservationKeyValue} on {@link ObservedAspect} check the
 * {@link ObservedAspect#setObservationKeyValueAnnotationHandler(ObservationKeyValueAnnotationHandler)}
 * method.
 *
 * @author Seungyong Hong
 */
public class ObservationKeyValueAnnotationHandler {

    private final AnnotationHandler<Observation.Context> highCardinalityAnnotationHandler;

    private final AnnotationHandler<Observation.Context> lowCardinalityAnnotationHandler;

    /**
     * Creates a new instance of {@link ObservationKeyValueAnnotationHandler}.
     * @param resolverProvider function to retrieve a {@link ValueResolver}
     * @param expressionResolverProvider function to retrieve a
     * {@link ValueExpressionResolver}
     */
    public ObservationKeyValueAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        this.highCardinalityAnnotationHandler = getAnnotationHandler(CardinalityType.HIGH, resolverProvider,
                expressionResolverProvider);
        this.lowCardinalityAnnotationHandler = getAnnotationHandler(CardinalityType.LOW, resolverProvider,
                expressionResolverProvider);
    }

    private AnnotationHandler<Observation.Context> getAnnotationHandler(CardinalityType cardinalityType,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        return new AnnotationHandler<>(ObservationKeyValueAnnotationHandler.getKeyValueConsumer(cardinalityType),
                resolverProvider, expressionResolverProvider, ObservationKeyValue.class, (annotation, object) -> {
                    ObservationKeyValue observationKeyValue = (ObservationKeyValue) annotation;

                    return KeyValue.of(ObservationKeyValueSupport.resolveTagKey(observationKeyValue),
                            ObservationKeyValueSupport.resolveTagValue(observationKeyValue, object, resolverProvider,
                                    expressionResolverProvider));
                }, (annotation, object) -> {
                    if (annotation.annotationType() != ObservationKeyValue.class) {
                        return false;
                    }

                    ObservationKeyValue observationKeyValue = (ObservationKeyValue) annotation;
                    if (observationKeyValue.cardinality() != cardinalityType) {
                        return false;
                    }

                    return true;
                });
    }

    private static BiConsumer<KeyValue, Observation.Context> getKeyValueConsumer(CardinalityType cardinalityType) {
        switch (cardinalityType) {
            case HIGH:
                return (keyValue, context) -> context.addHighCardinalityKeyValue(keyValue);
            case LOW:
                return (keyValue, context) -> context.addLowCardinalityKeyValue(keyValue);
            default:
                throw new IllegalArgumentException("Unknown cardinality type: " + cardinalityType);
        }
    }

    /**
     * Delegation method to
     * {@link AnnotationHandler#addAnnotatedParameters(Object, ProceedingJoinPoint)} high
     * and low annotation handlers.
     */
    public void addAnnotatedParameters(Observation.Context context, ProceedingJoinPoint pjp) {
        highCardinalityAnnotationHandler.addAnnotatedParameters(context, pjp);
        lowCardinalityAnnotationHandler.addAnnotatedParameters(context, pjp);
    }

    /**
     * Delegation method to
     * {@link AnnotationHandler#addAnnotatedMethodResult(Object, ProceedingJoinPoint, Object)}
     * high and low annotation handlers.
     */
    public void addAnnotatedMethodResult(Observation.Context context, ProceedingJoinPoint pjp,
            @Nullable Object result) {
        highCardinalityAnnotationHandler.addAnnotatedMethodResult(context, pjp, result);
        lowCardinalityAnnotationHandler.addAnnotatedMethodResult(context, pjp, result);
    }

}
