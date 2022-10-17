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
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>
 * AOP alliance advice for intercepting types or methods annotated with
 * {@link Timed @Timed}.<br>
 * The advice supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 * You might want to add tags programmatically to the {@link Timer}.<br>
 * In this case, the tags provider function
 * (<code>Function&lt;MethodInvocation, Iterable&lt;Tag&gt;&gt;</code>) can help. It
 * receives a {@link MethodInvocation} and returns the {@link Tag}s that will be attached
 * to the {@link Timer}.
 * </p>
 * <p>
 * You might also want to skip the {@link Timer} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes
 * the {@link Timed @Timed} annotation in some cases so that {@code TimedAspect} should
 * not intercept these methods. E.g.: Spring Boot does this for its controllers. By using
 * the skip predicate (<code>Predicate&lt;MethodInvocation&gt;</code>) you can tell the
 * {@code TimedAspect} when not to create a {@link Timer}.
 * <p>
 * Here's an example to disable {@link Timer} creation for Spring controllers:
 * </p>
 * <pre>
 * &#064;Bean
 * public TimedAspect timedAspect(MeterRegistry meterRegistry) {
 *     return new TimedAspect(meterRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(MethodInvocation invocation) {
 *     Class&lt;?&gt; targetClass = invocation.getThis().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nejc Korasa
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@NonNullApi
@Incubating(since = "1.0.0")
public class TimedAspect implements MethodInterceptor {

    private static final Predicate<MethodInvocation> DONT_SKIP_ANYTHING = pjp -> false;

    public static final String DEFAULT_METRIC_NAME = "method.timed";

    public static final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;

    private final Function<MethodInvocation, Iterable<Tag>> tagsBasedOnJoinPoint;

    private final Predicate<MethodInvocation> shouldSkip;

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
    public TimedAspect(MeterRegistry registry, Function<MethodInvocation, Iterable<Tag>> tagsBasedOnJoinPoint) {
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
    public TimedAspect(MeterRegistry registry, Predicate<MethodInvocation> shouldSkip) {
        this(registry, invocation -> Tags.of("class", invocation.getMethod().getDeclaringClass().getName(), "method",
                invocation.getMethod().getName()), shouldSkip);
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
    public TimedAspect(MeterRegistry registry, Function<MethodInvocation, Iterable<Tag>> tagsBasedOnJoinPoint,
            Predicate<MethodInvocation> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
        this.shouldSkip = shouldSkip;
    }

    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (shouldSkip.test(invocation)) {
            return invocation.proceed();
        }

        Method method = invocation.getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            timed = invocation.getThis().getClass().getAnnotation(Timed.class);
        }
        return timed == null ? invocation.proceed() : perform(invocation, timed, method);
    }

    private Object perform(MethodInvocation invocation, Timed timed, Method method) throws Throwable {
        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());

        return timed.longTask() ? processWithLongTaskTimer(invocation, timed, metricName, stopWhenCompleted)
                : processWithTimer(invocation, timed, metricName, stopWhenCompleted);
    }

    private Object processWithTimer(MethodInvocation invocation, Timed timed, String metricName,
            boolean stopWhenCompleted) throws Throwable {

        Timer.Sample sample = Timer.start(registry);

        if (stopWhenCompleted) {
            try {
                return ((CompletionStage<?>) invocation.proceed()).whenComplete((result,
                        throwable) -> record(invocation, timed, metricName, sample, getExceptionTag(throwable)));
            }
            catch (Exception ex) {
                record(invocation, timed, metricName, sample, ex.getClass().getSimpleName());
                throw ex;
            }
        }

        String exceptionClass = DEFAULT_EXCEPTION_TAG_VALUE;
        try {
            return invocation.proceed();
        }
        catch (Exception ex) {
            exceptionClass = ex.getClass().getSimpleName();
            throw ex;
        }
        finally {
            record(invocation, timed, metricName, sample, exceptionClass);
        }
    }

    private void record(MethodInvocation invocation, Timed timed, String metricName, Timer.Sample sample,
            String exceptionClass) {
        try {
            sample.stop(
                    Timer.builder(metricName).description(timed.description().isEmpty() ? null : timed.description())
                            .tags(timed.extraTags()).tags(EXCEPTION_TAG, exceptionClass)
                            .tags(tagsBasedOnJoinPoint.apply(invocation)).publishPercentileHistogram(timed.histogram())
                            .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                            .register(registry));
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    private String getExceptionTag(Throwable throwable) {

        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }

        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getCause().getClass().getSimpleName();
    }

    private Object processWithLongTaskTimer(MethodInvocation invocation, Timed timed, String metricName,
            boolean stopWhenCompleted) throws Throwable {

        Optional<LongTaskTimer.Sample> sample = buildLongTaskTimer(invocation, timed, metricName)
                .map(LongTaskTimer::start);

        if (stopWhenCompleted) {
            try {
                return ((CompletionStage<?>) invocation.proceed())
                        .whenComplete((result, throwable) -> sample.ifPresent(this::stopTimer));
            }
            catch (Exception ex) {
                sample.ifPresent(this::stopTimer);
                throw ex;
            }
        }

        try {
            return invocation.proceed();
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
    private Optional<LongTaskTimer> buildLongTaskTimer(MethodInvocation invocation, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                    .description(timed.description().isEmpty() ? null : timed.description()).tags(timed.extraTags())
                    .tags(tagsBasedOnJoinPoint.apply(invocation)).register(registry));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

}
