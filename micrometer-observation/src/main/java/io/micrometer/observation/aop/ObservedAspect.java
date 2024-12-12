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
import io.micrometer.observation.Observations;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.ObservationConvention;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with
 * {@link Observed @Observed}.<br>
 * The aspect supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 * You might want to add {@link io.micrometer.common.KeyValue}s programmatically to the
 * {@link Observation}.<br>
 * In this case, the {@link ObservationConvention} can help. It receives an
 * {@link ObservedAspectContext} that also contains the {@link ProceedingJoinPoint} and
 * returns the {@link io.micrometer.common.KeyValue}s that will be attached to the
 * {@link Observation}.
 * </p>
 * <p>
 * You might also want to skip the {@link Observation} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes
 * the {@link Observed @Observed} annotation in some cases so that {@code ObservedAspect}
 * should not intercept these methods. E.g.: Spring Boot does this for its controllers. By
 * using the skip predicate (<code>Predicate&lt;ProceedingJoinPoint&gt;</code>) you can
 * tell the {@code ObservedAspect} when not to create an {@link Observation}.
 *
 * Here's an example to disable {@link Observation} creation for Spring controllers:
 * </p>
 * <pre>
 * &#064;Bean
 * public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
 *     return new ObservedAspect(observationRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(ProceedingJoinPoint pjp) {
 *     Class&lt;?&gt; targetClass = pjp.getTarget().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * @author Jonatan Ivanov
 * @author Yanming Zhou
 * @author Jeonggi Kim
 * @since 1.10.0
 */
@Aspect
@NonNullApi
public class ObservedAspect {

    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    private final ObservationRegistry registry;

    @Nullable
    private final ObservationConvention<ObservedAspectContext> observationConvention;

    private final Predicate<ProceedingJoinPoint> shouldSkip;

    /**
     * Create an {@code ObservedAspect} with {@link Observations#getGlobalRegistry()}.
     *
     * This is for compile-time weaving.
     *
     * @since 1.14.0
     */
    public ObservedAspect() {
        this(Observations.getGlobalRegistry(), null, DONT_SKIP_ANYTHING);
    }

    public ObservedAspect(ObservationRegistry registry) {
        this(registry, null, DONT_SKIP_ANYTHING);
    }

    public ObservedAspect(ObservationRegistry registry,
            ObservationConvention<ObservedAspectContext> observationConvention) {
        this(registry, observationConvention, DONT_SKIP_ANYTHING);
    }

    public ObservedAspect(ObservationRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, null, shouldSkip);
    }

    public ObservedAspect(ObservationRegistry registry,
            @Nullable ObservationConvention<ObservedAspectContext> observationConvention,
            Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.observationConvention = observationConvention;
        this.shouldSkip = shouldSkip;
    }

    @Around("@within(io.micrometer.observation.annotation.Observed) && !@annotation(io.micrometer.observation.annotation.Observed) && execution(* *.*(..))")
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
        Observation observation = ObservedAspectObservationDocumentation.of(pjp, observed, this.registry,
                this.observationConvention);
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            observation.start();
            Observation.Scope scope = observation.openScope();
            try {
                Object result = pjp.proceed();
                if (result == null) {
                    stopObservation(observation, scope, null);
                    return result;
                }
                else {
                    CompletionStage<?> stage = (CompletionStage<?>) result;
                    return stage.whenComplete((res, error) -> stopObservation(observation, scope, error));
                }
            }
            catch (Throwable error) {
                stopObservation(observation, scope, error);
                throw error;
            }
            finally {
                scope.close();
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
