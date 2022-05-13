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

/**
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
@Aspect
@NonNullApi
public class ObservedAspect {

    private static final String DEFAULT_OBSERVATION_NAME = "method.observed";

    private final ObservationRegistry registry;

    public ObservedAspect(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Around("execution (@io.micrometer.observation.annotation.Observed * *.*(..))")
    @Nullable
    public Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Observed observed = method.getAnnotation(Observed.class);
        String observationName = observed.value() != null ? observed.value() : DEFAULT_OBSERVATION_NAME;
        Signature signature = pjp.getStaticPart().getSignature();

        return Observation.createNotStarted(observationName, registry)
                .contextualName(signature.getDeclaringType().getSimpleName() + "#" + signature.getName())
                .lowCardinalityKeyValue("class", signature.getDeclaringTypeName())
                .lowCardinalityKeyValue("method", signature.getName()).observeChecked(() -> pjp.proceed());
    }

}
