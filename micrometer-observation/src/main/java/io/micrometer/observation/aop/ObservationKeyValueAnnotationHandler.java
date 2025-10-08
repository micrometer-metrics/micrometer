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
package io.micrometer.observation.aop;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservationKeyValue;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.micrometer.observation.aop.CardinalityType.HIGH;
import static io.micrometer.observation.aop.CardinalityType.LOW;
import static io.micrometer.observation.aop.ObservationKeyValueSupport.resolveKey;
import static io.micrometer.observation.aop.ObservationKeyValueSupport.resolveValue;

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
        this.highCardinalityAnnotationHandler = createAnnotationHandler(HIGH, resolverProvider,
                expressionResolverProvider);
        this.lowCardinalityAnnotationHandler = createAnnotationHandler(LOW, resolverProvider,
                expressionResolverProvider);
    }

    private AnnotationHandler<Observation.Context> createAnnotationHandler(CardinalityType cardinalityType,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        return new AnnotationHandler<>(keyValueConsumer(cardinalityType), resolverProvider, expressionResolverProvider,
                ObservationKeyValue.class,
                (annotation, object) -> toKeyValue(annotation, object, resolverProvider, expressionResolverProvider),
                (annotation, object) -> validToAdd(annotation, cardinalityType));
    }

    private BiConsumer<KeyValue, Observation.Context> keyValueConsumer(CardinalityType cardinalityType) {
        switch (cardinalityType) {
            case HIGH:
                return (keyValue, context) -> context.addHighCardinalityKeyValue(keyValue);
            case LOW:
                return (keyValue, context) -> context.addLowCardinalityKeyValue(keyValue);
            default:
                throw new IllegalArgumentException("Unknown cardinality type: " + cardinalityType);
        }
    }

    private KeyValue toKeyValue(Annotation annotation, Object object,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        ObservationKeyValue observationKeyValue = (ObservationKeyValue) annotation;
        return KeyValue.of(resolveKey(observationKeyValue),
                resolveValue(observationKeyValue, object, resolverProvider, expressionResolverProvider));
    }

    private boolean validToAdd(Annotation annotation, CardinalityType cardinalityType) {
        if (annotation.annotationType() != ObservationKeyValue.class) {
            return false;
        }

        return ((ObservationKeyValue) annotation).cardinality() == cardinalityType;
    }

    /**
     * Delegation method to
     * {@link AnnotationHandler#addAnnotatedParameters(Object, ProceedingJoinPoint)} high
     * and low annotation handlers.
     */
    void addAnnotatedParameters(Observation.Context context, ProceedingJoinPoint pjp) {
        highCardinalityAnnotationHandler.addAnnotatedParameters(context, pjp);
        lowCardinalityAnnotationHandler.addAnnotatedParameters(context, pjp);
    }

    /**
     * Delegation method to
     * {@link AnnotationHandler#addAnnotatedMethodResult(Object, ProceedingJoinPoint, Object)}
     * high and low annotation handlers.
     */
    void addAnnotatedMethodResult(Observation.Context context, ProceedingJoinPoint pjp, @Nullable Object result) {
        highCardinalityAnnotationHandler.addAnnotatedMethodResult(context, pjp, result);
        lowCardinalityAnnotationHandler.addAnnotatedMethodResult(context, pjp, result);
    }

}
