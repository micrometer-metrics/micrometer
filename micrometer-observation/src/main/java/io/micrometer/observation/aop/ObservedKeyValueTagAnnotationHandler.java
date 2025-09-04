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

import java.util.function.Function;

import org.aspectj.lang.ProceedingJoinPoint;
import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservedKeyValueTag;

/**
 * Annotation handler for {@link ObservedKeyValueTag}. To add support for
 * {@link ObservedKeyValueTag} on {@link ObservedAspect} check the
 * {@link ObservedAspect#setObservedKeyValueTagAnnotationHandler(ObservedKeyValueTagAnnotationHandler)}
 * method.
 *
 * @author Seungyong Hong
 */
public class ObservedKeyValueTagAnnotationHandler {

    private final AnnotationHandler<Observation.Context> highCardinalityAnnotationHandler;

    private final AnnotationHandler<Observation.Context> lowCardinalityAnnotationHandler;

    /**
     * Creates a new instance of {@link ObservedKeyValueTagAnnotationHandler}.
     * @param resolverProvider function to retrieve a {@link ValueResolver}
     * @param expressionResolverProvider function to retrieve a
     * {@link ValueExpressionResolver}
     */
    public ObservedKeyValueTagAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        this.highCardinalityAnnotationHandler = new AnnotationHandler<>(
                (keyValue, context) -> context.addHighCardinalityKeyValue(keyValue), resolverProvider,
                expressionResolverProvider, ObservedKeyValueTag.class, (annotation, object) -> {
                    ObservedKeyValueTag observedKeyValueTag = (ObservedKeyValueTag) annotation;

                    return KeyValue.of(ObservedKeyValueTagSupport.resolveTagKey(observedKeyValueTag),
                            ObservedKeyValueTagSupport.resolveTagValue(observedKeyValueTag, object, resolverProvider,
                                    expressionResolverProvider));
                }, (annotation, object) -> {
                    if (annotation.annotationType() != ObservedKeyValueTag.class) {
                        return false;
                    }

                    ObservedKeyValueTag observedKeyValueTag = (ObservedKeyValueTag) annotation;
                    if (observedKeyValueTag.cardinality() != CardinalityType.HIGH) {
                        return false;
                    }

                    return true;
                });
        this.lowCardinalityAnnotationHandler = new AnnotationHandler<>(
                (keyValue, context) -> context.addLowCardinalityKeyValue(keyValue), resolverProvider,
                expressionResolverProvider, ObservedKeyValueTag.class, (annotation, object) -> {
                    ObservedKeyValueTag observedKeyValueTag = (ObservedKeyValueTag) annotation;

                    return KeyValue.of(ObservedKeyValueTagSupport.resolveTagKey(observedKeyValueTag),
                            ObservedKeyValueTagSupport.resolveTagValue(observedKeyValueTag, object, resolverProvider,
                                    expressionResolverProvider));
                }, (annotation, object) -> {
                    if (annotation.annotationType() != ObservedKeyValueTag.class) {
                        return false;
                    }

                    ObservedKeyValueTag observedKeyValueTag = (ObservedKeyValueTag) annotation;
                    if (observedKeyValueTag.cardinality() != CardinalityType.LOW) {
                        return false;
                    }

                    return true;
                });
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

}
