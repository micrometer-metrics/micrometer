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
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class is able to find all methods annotated with the Micrometer annotations. All
 * methods mean that if you have both an interface and an implementation annotated with
 * Micrometer annotations then this class is capable of finding both of them and merging
 * into one set of information.
 * <p>
 * This information is then used to add proper tags to objects such as span or timer from
 * the method arguments that are annotated with a tag related annotation.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @author Christian Schwerdtfeger
 * @since 1.11.0
 */
public class TagAnnotationHandler<T> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(TagAnnotationHandler.class);

    private final BiConsumer<KeyValue, T> tagConsumer;

    private final Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider;

    private final Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider;

    private final Class<? extends Annotation> tagClass;

    private final BiFunction<Annotation, Object, KeyValue> toKeyValue;

    public TagAnnotationHandler(BiConsumer<KeyValue, T> tagConsumer,
            Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider,
            Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider,
            Class<? extends Annotation> tagClass, BiFunction<Annotation, Object, KeyValue> toKeyValue) {
        this.tagConsumer = tagConsumer;
        this.resolverProvider = resolverProvider;
        this.expressionResolverProvider = expressionResolverProvider;
        this.tagClass = tagClass;
        this.toKeyValue = toKeyValue;
    }

    public void addAnnotatedParameters(T objectToTag, ProceedingJoinPoint pjp) {
        try {
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            List<AnnotatedParameter> annotatedParameters = AnnotationUtils.findAnnotatedParameters(tagClass, method,
                    pjp.getArgs());
            getAnnotationsFromInterfaces(pjp, method, annotatedParameters);
            addAnnotatedArguments(objectToTag, annotatedParameters);
        }
        catch (SecurityException ex) {
            log.error("Exception occurred while trying to add annotated parameters", ex);
        }
    }

    private void getAnnotationsFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod,
            List<AnnotatedParameter> annotatedParameters) {
        Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
        if (implementedInterfaces.length > 0) {
            for (Class<?> implementedInterface : implementedInterfaces) {
                for (Method methodFromInterface : implementedInterface.getMethods()) {
                    if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
                        List<AnnotatedParameter> annotatedParametersForActualMethod = AnnotationUtils
                            .findAnnotatedParameters(tagClass, methodFromInterface, pjp.getArgs());
                        mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
                    }
                }
            }
        }
    }

    private boolean methodsAreTheSame(Method mostSpecificMethod, Method method1) {
        return method1.getName().equals(mostSpecificMethod.getName())
                && Arrays.equals(method1.getParameterTypes(), mostSpecificMethod.getParameterTypes());
    }

    private void mergeAnnotatedParameters(List<AnnotatedParameter> annotatedParametersIndices,
            List<AnnotatedParameter> annotatedParametersIndicesForActualMethod) {
        for (AnnotatedParameter container : annotatedParametersIndicesForActualMethod) {
            final int index = container.parameterIndex;
            boolean parameterContained = false;
            for (AnnotatedParameter parameterContainer : annotatedParametersIndices) {
                if (parameterContainer.parameterIndex == index) {
                    parameterContained = true;
                    break;
                }
            }
            if (!parameterContained) {
                annotatedParametersIndices.add(container);
            }
        }
    }

    private void addAnnotatedArguments(T objectToTag, List<AnnotatedParameter> toBeAdded) {
        for (AnnotatedParameter container : toBeAdded) {
            KeyValue keyValue = toKeyValue.apply(container.annotation, container.argument);
            tagConsumer.accept(keyValue, objectToTag);
        }
    }

    public Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> getResolverProvider() {
        return resolverProvider;
    }

    public Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> getExpressionResolverProvider() {
        return expressionResolverProvider;
    }

}
