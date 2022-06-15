/*
 * Copyright 2022 VMware, Inc.
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

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
@Aspect
@NonNullApi
public class ObservedAspect {

    private static final String DEFAULT_OBSERVATION_NAME = "method.observed";

    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    private final ObservationRegistry registry;

    @Nullable
    private final Observation.KeyValuesProvider<ObservedAspectContext> keyValuesProvider;

    private final Predicate<ProceedingJoinPoint> shouldSkip;

    public ObservedAspect(ObservationRegistry registry) {
        this(registry, null, DONT_SKIP_ANYTHING);
    }

    public ObservedAspect(ObservationRegistry registry,
            Observation.KeyValuesProvider<ObservedAspectContext> keyValuesProvider) {
        this(registry, keyValuesProvider, DONT_SKIP_ANYTHING);
    }

    public ObservedAspect(ObservationRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, null, shouldSkip);
    }

    public ObservedAspect(ObservationRegistry registry,
            @Nullable Observation.KeyValuesProvider<ObservedAspectContext> keyValuesProvider,
            Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.keyValuesProvider = keyValuesProvider;
        this.shouldSkip = shouldSkip;
    }

    @Around("@within(io.micrometer.observation.annotation.Observed)")
    @Nullable
    public Object observeClass(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Observed observed = getDeclaringClass(pjp).getAnnotation(Observed.class);
        return observe(pjp, method, observed);
    }

    @Around("execution (@io.micrometer.observation.annotation.Observed * *.*(..))")
    @Nullable
    public Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = getMethod(pjp);
        Observed observed = method.getAnnotation(Observed.class);
        return observe(pjp, method, observed);
    }

    private Object observe(ProceedingJoinPoint pjp, Method method, Observed observed) throws Throwable {
        String name = observed.name().isEmpty() ? DEFAULT_OBSERVATION_NAME : observed.name();
        Signature signature = pjp.getStaticPart().getSignature();
        String contextualName = observed.contextualName().isEmpty() ? getContextualName(signature)
                : observed.contextualName();

        Observation observation = Observation.createNotStarted(name, new ObservedAspectContext(pjp), registry)
                .contextualName(contextualName).lowCardinalityKeyValue("class", signature.getDeclaringTypeName())
                .lowCardinalityKeyValue("method", signature.getName())
                .lowCardinalityKeyValues(KeyValues.of(observed.lowCardinalityKeyValues()));

        if (this.keyValuesProvider != null) {
            observation.keyValuesProvider(this.keyValuesProvider);
        }

        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            observation.start();
            Observation.Scope scope = observation.openScope();
            try {
                return ((CompletionStage<?>) pjp.proceed())
                        .whenComplete((result, error) -> stopObservation(observation, scope, error));
            }
            catch (Throwable error) {
                stopObservation(observation, scope, error);
                throw error;
            }
        }
        else {
            return observation.observeChecked(() -> pjp.proceed());
        }
    }

    private Class<?> getDeclaringClass(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Observed.class)) {
            return pjp.getTarget().getClass();
        }

        return declaringClass;
    }

    private Method getMethod(ProceedingJoinPoint pjp) throws NoSuchMethodException {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (method.getAnnotation(Observed.class) == null) {
            return pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
        }

        return method;
    }

    private String getContextualName(Signature signature) {
        return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
    }

    private void stopObservation(Observation observation, Observation.Scope scope, @Nullable Throwable error) {
        if (error != null) {
            observation.error(error);
        }
        scope.close();
        observation.stop();
    }

    public static class ObservedAspectContext extends Observation.Context {

        private final ProceedingJoinPoint proceedingJoinPoint;

        public ObservedAspectContext(ProceedingJoinPoint proceedingJoinPoint) {
            this.proceedingJoinPoint = proceedingJoinPoint;
        }

        public ProceedingJoinPoint getProceedingJoinPoint() {
            return this.proceedingJoinPoint;
        }

    }

}
