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
import java.util.*;
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
public class AnnotationHandler<T> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(AnnotationHandler.class);

    private final BiConsumer<KeyValue, T> keyValueConsumer;

    private final Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider;

    private final Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider;

    private final Class<? extends Annotation> annotationClass;

    private final BiFunction<Annotation, Object, KeyValue> toKeyValue;

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
        this.keyValueConsumer = keyValueConsumer;
        this.resolverProvider = resolverProvider;
        this.expressionResolverProvider = expressionResolverProvider;
        this.annotationClass = annotation;
        this.toKeyValue = toKeyValue;
    }

    /**
     * Modifies the object with {@link KeyValue} related information.
     * @param objectToModify object to modify
     * @param pjp proceeding join point
     */
    public void addAnnotatedParameters(T objectToModify, ProceedingJoinPoint pjp) {
        try {
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            method = tryToTakeMethodFromTargetClass(pjp, method);
            List<AnnotatedParameter> annotatedParameters = AnnotationUtils.findAnnotatedParameters(annotationClass,
                    method, pjp.getArgs());
            getAnnotationsFromInterfaces(pjp, method, annotatedParameters);
            addAnnotatedArguments(objectToModify, annotatedParameters);
        }
        catch (Exception ex) {
            log.error("Exception occurred while trying to add annotated parameters", ex);
        }
    }

    private static Method tryToTakeMethodFromTargetClass(ProceedingJoinPoint pjp, Method method) {
        try {
            return pjp.getTarget().getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
        }
        catch (NoSuchMethodException ex) {
            // matching method not found - will be taken from parent
        }
        return method;
    }

    private void getAnnotationsFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod,
            List<AnnotatedParameter> annotatedParameters) {
        Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
        for (Class<?> implementedInterface : implementedInterfaces) {
            for (Method methodFromInterface : implementedInterface.getMethods()) {
                if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
                    List<AnnotatedParameter> annotatedParametersForActualMethod = AnnotationUtils
                        .findAnnotatedParameters(annotationClass, methodFromInterface, pjp.getArgs());
                    // annotations for a single parameter can be `duplicated` by the ones
                    // from parent interface,
                    // however later on during key-based deduplication the ones from
                    // specific method(target class)
                    // will take precedence
                    annotatedParameters.addAll(annotatedParametersForActualMethod);
                }
            }
        }
    }

    private boolean methodsAreTheSame(Method mostSpecificMethod, Method method) {
        return method.getName().equals(mostSpecificMethod.getName())
                && Arrays.equals(method.getParameterTypes(), mostSpecificMethod.getParameterTypes());
    }

    private void addAnnotatedArguments(T objectToModify, List<AnnotatedParameter> toBeAdded) {
        Set<String> seen = new HashSet<>();
        for (AnnotatedParameter container : toBeAdded) {
            KeyValue keyValue = toKeyValue.apply(container.annotation, container.argument);
            if (seen.add(keyValue.getKey())) {
                keyValueConsumer.accept(keyValue, objectToModify);
            }
        }
    }

    public Function<Class<? extends ValueResolver>, ? extends ValueResolver> getResolverProvider() {
        return resolverProvider;
    }

    public Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> getExpressionResolverProvider() {
        return expressionResolverProvider;
    }

}
