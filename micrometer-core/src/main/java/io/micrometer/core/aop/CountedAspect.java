/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.aop;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>
 * Aspect responsible for intercepting all methods annotated with the
 * {@link Counted @Counted} annotation and recording a few counter metrics about their
 * execution status.<br>
 * The aspect supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 * You might want to add tags programmatically to the {@link Counter}.<br>
 * In this case, the tags provider function
 * (<code>Function&lt;ProceedingJoinPoint, Iterable&lt;Tag&gt;&gt;</code>) can help. It
 * receives a {@link ProceedingJoinPoint} and returns the {@link Tag}s that will be
 * attached to the {@link Counter}.
 * </p>
 * <p>
 * You might also want to skip the {@link Counter} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes
 * the {@link Counted @Counted} annotation in some cases so that {@code CountedAspect}
 * should not intercept these methods. By using the skip predicate
 * (<code>Predicate&lt;ProceedingJoinPoint&gt;</code>) you can tell the
 * {@code CountedAspect} when not to create a {@link Counter}.
 *
 * Here's a theoretic example to disable {@link Counter} creation for Spring controllers:
 * </p>
 * <pre>
 * &#064;Bean
 * public CountedAspect countedAspect(MeterRegistry meterRegistry) {
 *     return new CountedAspect(meterRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(ProceedingJoinPoint pjp) {
 *     Class&lt;?&gt; targetClass = pjp.getTarget().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * @author Ali Dehghani
 * @author Jonatan Ivanov
 * @author Johnny Lim
 * @author Yanming Zhou
 * @author Jeonggi Kim
 * @since 1.2.0
 * @see Counted
 */
@Aspect
@NonNullApi
public class CountedAspect {

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(CountedAspect.class);

    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    public final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    public final String RESULT_TAG_FAILURE_VALUE = "failure";

    public final String RESULT_TAG_SUCCESS_VALUE = "success";

    /**
     * The tag name to encapsulate the method execution status.
     */
    private static final String RESULT_TAG = "result";

    /**
     * The tag name to encapsulate the exception thrown by the intercepted method.
     */
    private static final String EXCEPTION_TAG = "exception";

    /**
     * Where we're going register metrics.
     */
    private final MeterRegistry registry;

    /**
     * A function to produce additional tags for any given join point.
     */
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    /**
     * A predicate that decides if counter creation should be skipped for the given join
     * point.
     */
    private final Predicate<ProceedingJoinPoint> shouldSkip;

    private CountedMeterTagAnnotationHandler meterTagAnnotationHandler;

    /**
     * Creates a {@code CountedAspect} instance with {@link Metrics#globalRegistry}.
     *
     * @since 1.7.0
     */
    public CountedAspect() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a {@code CountedAspect} instance with the given {@code registry}.
     * @param registry Where we're going to register metrics.
     */
    public CountedAspect(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code CountedAspect} instance with the given {@code registry} and tags
     * provider function.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public CountedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this(registry, tagsBasedOnJoinPoint, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code CountedAspect} instance with the given {@code registry} and skip
     * predicate.
     * @param registry Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the counter should be skipped
     * or not.
     * @since 1.7.0
     */
    public CountedAspect(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, pjp -> Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(), "method",
                pjp.getStaticPart().getSignature().getName()), shouldSkip);
    }

    /**
     * Creates a {@code CountedAspect} instance with the given {@code registry}, tags
     * provider function and skip predicate.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip A predicate to decide if creating the counter should be skipped
     * or not.
     * @since 1.7.0
     */
    public CountedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint,
            Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = makeSafe(tagsBasedOnJoinPoint);
        this.shouldSkip = shouldSkip;
    }

    private Function<ProceedingJoinPoint, Iterable<Tag>> makeSafe(
            Function<ProceedingJoinPoint, Iterable<Tag>> function) {
        return pjp -> {
            try {
                return function.apply(pjp);
            }
            catch (Throwable t) {
                WARN_THEN_DEBUG_LOGGER
                    .log("Exception thrown from the tagsBasedOnJoinPoint function configured on CountedAspect.", t);
                return Tags.empty();
            }
        };
    }

    @Around("@within(io.micrometer.core.annotation.Counted) && !@annotation(io.micrometer.core.annotation.Counted) && execution(* *(..))")
    @Nullable
    public Object countedClass(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Counted.class)) {
            declaringClass = pjp.getTarget().getClass();
        }
        Counted counted = declaringClass.getAnnotation(Counted.class);

        return perform(pjp, counted);
    }

    /**
     * Intercept methods annotated with the {@link Counted} annotation and expose a few
     * counters about their execution status. By default, this aspect records both failed
     * and successful attempts. If the {@link Counted#recordFailuresOnly()} is set to
     * {@code true}, then the aspect would record only failed attempts. In case of a
     * failure, the aspect tags the counter with the simple name of the thrown exception.
     *
     * <p>
     * When the annotated method returns a {@link CompletionStage} or any of its
     * subclasses, the counters will be incremented only when the {@link CompletionStage}
     * is completed. If completed exceptionally a failure is recorded, otherwise if
     * {@link Counted#recordFailuresOnly()} is set to {@code false}, a success is
     * recorded.
     * @param pjp Encapsulates some information about the intercepted area.
     * @param counted The annotation.
     * @return Whatever the intercepted method returns.
     * @throws Throwable When the intercepted method throws one.
     */
    @Around(value = "@annotation(counted) && execution(* *(..))", argNames = "pjp,counted")
    @Nullable
    public Object interceptAndRecord(ProceedingJoinPoint pjp, Counted counted) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        return perform(pjp, counted);
    }

    private Object perform(ProceedingJoinPoint pjp, Counted counted) throws Throwable {
        final Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());

        if (stopWhenCompleted) {
            try {
                Object result = pjp.proceed();
                if (result == null) {
                    if (!counted.recordFailuresOnly()) {
                        record(pjp, counted, DEFAULT_EXCEPTION_TAG_VALUE, RESULT_TAG_SUCCESS_VALUE);
                    }
                    return result;
                }
                else {
                    CompletionStage<?> stage = ((CompletionStage<?>) result);
                    return stage.whenComplete((res, throwable) -> recordCompletionResult(pjp, counted, throwable));
                }
            }
            catch (Throwable e) {
                record(pjp, counted, e.getClass().getSimpleName(), RESULT_TAG_FAILURE_VALUE);
                throw e;
            }
        }

        try {
            Object result = pjp.proceed();
            if (!counted.recordFailuresOnly()) {
                record(pjp, counted, DEFAULT_EXCEPTION_TAG_VALUE, RESULT_TAG_SUCCESS_VALUE);
            }
            return result;
        }
        catch (Throwable e) {
            record(pjp, counted, e.getClass().getSimpleName(), RESULT_TAG_FAILURE_VALUE);
            throw e;
        }
    }

    private void recordCompletionResult(ProceedingJoinPoint pjp, Counted counted, Throwable throwable) {

        if (throwable != null) {
            String exceptionTagValue = throwable.getCause() == null ? throwable.getClass().getSimpleName()
                    : throwable.getCause().getClass().getSimpleName();
            record(pjp, counted, exceptionTagValue, RESULT_TAG_FAILURE_VALUE);
        }
        else if (!counted.recordFailuresOnly()) {
            record(pjp, counted, DEFAULT_EXCEPTION_TAG_VALUE, RESULT_TAG_SUCCESS_VALUE);
        }

    }

    private void record(ProceedingJoinPoint pjp, Counted counted, String exception, String result) {
        try {
            Counter.Builder builder = Counter.builder(counted.value())
                .description(counted.description().isEmpty() ? null : counted.description())
                .tags(counted.extraTags())
                .tag(EXCEPTION_TAG, exception)
                .tag(RESULT_TAG, result)
                .tags(tagsBasedOnJoinPoint.apply(pjp));
            if (meterTagAnnotationHandler != null) {
                meterTagAnnotationHandler.addAnnotatedParameters(builder, pjp);
            }
            builder.register(registry).increment();
        }
        catch (Throwable ex) {
            WARN_THEN_DEBUG_LOGGER.log("Failed to record.", ex);
        }
    }

    /**
     * Setting this enables support for {@link MeterTag}.
     * @param meterTagAnnotationHandler meter tag annotation handler
     */
    public void setMeterTagAnnotationHandler(CountedMeterTagAnnotationHandler meterTagAnnotationHandler) {
        this.meterTagAnnotationHandler = meterTagAnnotationHandler;
    }

}
