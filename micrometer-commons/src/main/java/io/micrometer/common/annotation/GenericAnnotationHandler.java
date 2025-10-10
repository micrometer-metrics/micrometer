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
import io.micrometer.common.Keyed;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class GenericAnnotationHandler<K extends Keyed, T> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(GenericAnnotationHandler.class);

    private final BiConsumer<K, T> consumer;

    private final Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider;

    private final Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider;

    private final Class<? extends Annotation> annotationClass;

    private final BiFunction<Annotation, Object, K> producer;

    /**
     * Creates a new instance of {@link GenericAnnotationHandler}.
     * @param consumer consumer that takes a {@link KeyValue} and mutates the {@code <T>}
     * type
     * @param resolverProvider function converting a class extending a
     * {@link ValueResolver} to an instance of that class
     * @param expressionResolverProvider function converting a class extending a
     * {@link ValueExpressionResolver} to an instance of that class
     * @param annotation annotation containing {@link KeyValue} related information
     * @param producer function converting the annotation and the expression or annotation
     * value to a {@link KeyValue}
     */
    public GenericAnnotationHandler(BiConsumer<K, T> consumer,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider,
            Class<? extends Annotation> annotation, BiFunction<Annotation, Object, K> producer) {
        this.consumer = consumer;
        this.resolverProvider = resolverProvider;
        this.expressionResolverProvider = expressionResolverProvider;
        this.annotationClass = annotation;
        this.producer = producer;
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
            List<AnnotatedObject> annotatedParameters = AnnotationUtils.findAnnotatedParameters(annotationClass, method,
                    pjp.getArgs());
            addAnnotatedParametersFromInterfaces(pjp, method, annotatedParameters);
            addAnnotatedObjects(objectToModify, annotatedParameters);
        }
        catch (Exception ex) {
            log.error("Exception occurred while trying to add annotated parameters", ex);
        }
    }

    /**
     * Modifies the object with {@link KeyValue} information based on the method result.
     * @param objectToModify object to modify
     * @param pjp proceeding join point
     * @param result method return value
     * @since 1.15.0
     */
    public void addAnnotatedMethodResult(T objectToModify, ProceedingJoinPoint pjp, @Nullable Object result) {
        try {
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            method = tryToTakeMethodFromTargetClass(pjp, method);

            List<AnnotatedObject> annotatedResult = new ArrayList<>();
            Arrays.stream(method.getAnnotationsByType(annotationClass))
                .map(annotation -> new AnnotatedObject(annotation, result))
                .forEach(annotatedResult::add);
            getMethodAnnotationsFromInterfaces(pjp, method).stream()
                .map(annotation -> new AnnotatedObject(annotation, result))
                .forEach(annotatedResult::add);

            addAnnotatedObjects(objectToModify, annotatedResult);
        }
        catch (Exception ex) {
            log.error("Exception occurred while trying to add annotated method result", ex);
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

    private void addAnnotatedParametersFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod,
            List<AnnotatedObject> annotatedParameters) {
        traverseInterfacesHierarchy(pjp, mostSpecificMethod, method -> {
            List<AnnotatedObject> annotatedParametersForActualMethod = AnnotationUtils
                .findAnnotatedParameters(annotationClass, method, pjp.getArgs());
            // annotations for a single parameter can be `duplicated` by the ones
            // from parent interface,
            // however later on during key-based deduplication the ones from
            // specific method(target class)
            // will take precedence
            annotatedParameters.addAll(annotatedParametersForActualMethod);
        });
    }

    private void traverseInterfacesHierarchy(ProceedingJoinPoint pjp, Method mostSpecificMethod,
            Consumer<Method> consumer) {
        Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
        for (Class<?> implementedInterface : implementedInterfaces) {
            for (Method methodFromInterface : implementedInterface.getMethods()) {
                if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
                    consumer.accept(methodFromInterface);
                }
            }
        }
    }

    private List<Annotation> getMethodAnnotationsFromInterfaces(ProceedingJoinPoint pjp, Method mostSpecificMethod) {
        List<Annotation> allAnnotations = new ArrayList<>();
        traverseInterfacesHierarchy(pjp, mostSpecificMethod,
                method -> allAnnotations.addAll(Arrays.asList(method.getAnnotationsByType(annotationClass))));
        return allAnnotations;
    }

    private boolean methodsAreTheSame(Method mostSpecificMethod, Method method) {
        return method.getName().equals(mostSpecificMethod.getName())
                && Arrays.equals(method.getParameterTypes(), mostSpecificMethod.getParameterTypes());
    }

    private void addAnnotatedObjects(T objectToModify, List<AnnotatedObject> toBeAdded) {
        Set<String> seen = new HashSet<>();
        for (AnnotatedObject container : toBeAdded) {
            K keyedData = producer.apply(container.annotation, container.object);
            if (seen.add(keyedData.getKey())) {
                consumer.accept(keyedData, objectToModify);
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
