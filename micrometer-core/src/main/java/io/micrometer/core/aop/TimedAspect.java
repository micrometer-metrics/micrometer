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

import io.micrometer.common.KeyValue;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.TimeUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with
 * {@link Timed @Timed}.<br>
 * The aspect supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 * You might want to add tags programmatically to the {@link Timer}.<br>
 * In this case, the tags provider function
 * (<code>Function&lt;ProceedingJoinPoint, Iterable&lt;Tag&gt;&gt;</code>) can help. It
 * receives a {@link ProceedingJoinPoint} and returns the {@link Tag}s that will be
 * attached to the {@link Timer}.
 * </p>
 * <p>
 * You might also want to skip the {@link Timer} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes
 * the {@link Timed @Timed} annotation in some cases so that {@code TimedAspect} should
 * not intercept these methods. E.g.: Spring Boot does this for its controllers. By using
 * the skip predicate (<code>Predicate&lt;ProceedingJoinPoint&gt;</code>) you can tell the
 * {@code TimedAspect} when not to create a {@link Timer}.
 *
 * Here's an example to disable {@link Timer} creation for Spring controllers:
 * </p>
 * <pre>
 * &#064;Bean
 * public TimedAspect timedAspect(MeterRegistry meterRegistry) {
 *     return new TimedAspect(meterRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(ProceedingJoinPoint pjp) {
 *     Class&lt;?&gt; targetClass = pjp.getTarget().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * To add support for {@link MeterTag} annotations set the
 * {@link MeterTagAnnotationHandler} via
 * {@link TimedAspect#setMeterTagAnnotationHandler(MeterTagAnnotationHandler)}.
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nejc Korasa
 * @author Jonatan Ivanov
 * @author Yanming Zhou
 * @author Jeonggi Kim
 * @since 1.0.0
 */
@Aspect
@NullMarked
@Incubating(since = "1.0.0")
public class TimedAspect {

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(TimedAspect.class);

    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    public static final String DEFAULT_METRIC_NAME = "method.timed";

    public static final String DEFAULT_EXCEPTION_TAG_VALUE = KeyValue.NONE_VALUE;

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;

    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    private final Predicate<ProceedingJoinPoint> shouldSkip;

    private @Nullable MeterTagAnnotationHandler meterTagAnnotationHandler;

    /**
     * Creates a {@code TimedAspect} instance with {@link Metrics#globalRegistry}.
     *
     * @since 1.2.0
     */
    public TimedAspect() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}.
     * @param registry Where we're going to register metrics.
     */
    public TimedAspect(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and tags
     * provider function.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this(registry, tagsBasedOnJoinPoint, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and skip
     * predicate.
     * @param registry Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, pjp -> Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(), "method",
                pjp.getStaticPart().getSignature().getName()), shouldSkip);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}, tags
     * provider function and skip predicate.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint,
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
                    .log("Exception thrown from the tagsBasedOnJoinPoint function configured on TimedAspect.", t);
                return Tags.empty();
            }
        };
    }

    @Around("@within(io.micrometer.core.annotation.Timed) && !@annotation(io.micrometer.core.annotation.Timed) && execution(* *(..))")
    public @Nullable Object timedClass(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Timed.class)) {
            declaringClass = pjp.getTarget().getClass();
        }
        Timed timed = declaringClass.getAnnotation(Timed.class);

        return perform(pjp, timed, method);
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    public @Nullable Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timed = method.getAnnotation(Timed.class);
        }

        return perform(pjp, timed, method);
    }

    private @Nullable Object perform(ProceedingJoinPoint pjp, Timed timed, Method method) throws Throwable {
        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());

        if (!timed.longTask()) {
            return processWithTimer(pjp, timed, metricName, stopWhenCompleted);
        }
        else {
            return processWithLongTaskTimer(pjp, timed, metricName, stopWhenCompleted);
        }
    }

    private @Nullable Object processWithTimer(ProceedingJoinPoint pjp, Timed timed, String metricName,
            boolean stopWhenCompleted) throws Throwable {

        Timer.Sample sample = Timer.start(registry);

        if (stopWhenCompleted) {
            try {
                Object result = pjp.proceed();
                if (result == null) {
                    record(pjp, result, timed, metricName, sample, DEFAULT_EXCEPTION_TAG_VALUE);
                    return result;
                }
                else {
                    CompletionStage<?> stage = ((CompletionStage<?>) result);
                    return stage.whenComplete((res, throwable) -> record(pjp, result, timed, metricName, sample,
                            getExceptionTag(throwable)));
                }
            }
            catch (Throwable e) {
                record(pjp, null, timed, metricName, sample, e.getClass().getSimpleName());
                throw e;
            }
        }

        String exceptionClass = DEFAULT_EXCEPTION_TAG_VALUE;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        }
        catch (Throwable e) {
            exceptionClass = e.getClass().getSimpleName();
            throw e;
        }
        finally {
            record(pjp, result, timed, metricName, sample, exceptionClass);
        }
    }

    private void record(ProceedingJoinPoint pjp, @Nullable Object methodResult, Timed timed, String metricName,
            Timer.Sample sample, String exceptionClass) {
        try {
            sample.stop(recordBuilder(pjp, methodResult, timed, metricName, exceptionClass).register(registry));
        }
        catch (Exception e) {
            WARN_THEN_DEBUG_LOGGER.log("Failed to record.", e);
        }
    }

    private Timer.Builder recordBuilder(ProceedingJoinPoint pjp, @Nullable Object methodResult, Timed timed,
            String metricName, String exceptionClass) {
        @SuppressWarnings("NullTernary")
        Timer.Builder builder = Timer.builder(metricName)
            .description(timed.description().isEmpty() ? null : timed.description())
            .tags(timed.extraTags())
            .tags(EXCEPTION_TAG, exceptionClass)
            .tags(tagsBasedOnJoinPoint.apply(pjp))
            .publishPercentileHistogram(timed.histogram())
            .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
            .serviceLevelObjectives(
                    timed.serviceLevelObjectives().length > 0 ? Arrays.stream(timed.serviceLevelObjectives())
                        .mapToObj(s -> Duration.ofNanos((long) TimeUtils.secondsToUnit(s, TimeUnit.NANOSECONDS)))
                        .toArray(Duration[]::new) : null);

        if (meterTagAnnotationHandler != null) {
            meterTagAnnotationHandler.addAnnotatedParameters(builder, pjp);
            meterTagAnnotationHandler.addAnnotatedMethodResult(builder, pjp, methodResult);
        }
        return builder;
    }

    private String getExceptionTag(@Nullable Throwable throwable) {

        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }

        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getCause().getClass().getSimpleName();
    }

    private @Nullable Object processWithLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName,
            boolean stopWhenCompleted) throws Throwable {

        Optional<LongTaskTimer.Sample> sample = buildLongTaskTimer(pjp, timed, metricName).map(LongTaskTimer::start);

        if (stopWhenCompleted) {
            try {
                Object result = pjp.proceed();
                if (result == null) {
                    sample.ifPresent(this::stopTimer);
                    return result;
                }
                else {
                    CompletionStage<?> stage = ((CompletionStage<?>) result);
                    return stage.whenComplete((res, throwable) -> sample.ifPresent(this::stopTimer));
                }
            }
            catch (Throwable e) {
                sample.ifPresent(this::stopTimer);
                throw e;
            }
        }

        try {
            return pjp.proceed();
        }
        finally {
            sample.ifPresent(this::stopTimer);
        }
    }

    private void stopTimer(LongTaskTimer.Sample sample) {
        try {
            sample.stop();
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    /**
     * Secure long task timer creation - it should not disrupt the application flow in
     * case of exception
     */
    private Optional<LongTaskTimer> buildLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                .description(timed.description().isEmpty() ? null : timed.description())
                .tags(timed.extraTags())
                .tags(tagsBasedOnJoinPoint.apply(pjp))
                .register(registry));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Setting this enables support for {@link MeterTag}.
     * @param meterTagAnnotationHandler meter tag annotation handler
     */
    public void setMeterTagAnnotationHandler(MeterTagAnnotationHandler meterTagAnnotationHandler) {
        this.meterTagAnnotationHandler = meterTagAnnotationHandler;
    }

}
