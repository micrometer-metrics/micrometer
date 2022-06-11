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

    @Around("execution (@io.micrometer.observation.annotation.Observed * *.*(..))")
    @Nullable
    public Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = getMethod(pjp);
        Observed observed = method.getAnnotation(Observed.class);
        String observationName = observed.value().isEmpty() ? DEFAULT_OBSERVATION_NAME : observed.value();
        Signature signature = pjp.getStaticPart().getSignature();

        Observation observation = Observation
                .createNotStarted(observationName, new ObservedAspectContext(pjp), registry)
                .contextualName(signature.getDeclaringType().getSimpleName() + "#" + signature.getName())
                // .longTask(observed.longTask()) // TODO: after the longTask PR is merged
                .lowCardinalityKeyValue("class", signature.getDeclaringTypeName())
                .lowCardinalityKeyValue("method", signature.getName());

        if (this.keyValuesProvider != null) {
            observation.keyValuesProvider(this.keyValuesProvider);
        }

        return observation.observeChecked(() -> pjp.proceed());
    }

    private Method getMethod(ProceedingJoinPoint pjp) throws NoSuchMethodException {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (method.getAnnotation(Observed.class) == null) {
            return pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
        }

        return method;
    }

    public static class ObservedAspectContext extends Observation.Context {

        private final ProceedingJoinPoint proceedingJoinPoint;

        public ObservedAspectContext(ProceedingJoinPoint proceedingJoinPoint) {
            this.proceedingJoinPoint = proceedingJoinPoint;
        }

        public ProceedingJoinPoint getProceedingJoinPoint() {
            return proceedingJoinPoint;
        }

    }

}
