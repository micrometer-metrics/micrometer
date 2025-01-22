package io.micrometer.core.aop;


import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import io.micrometer.core.annotation.Gauged;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with
 * {@link Gauged @Gauged}. This aspect supports programmatic customizations
 * through constructor-injectable custom logic.
 * </p>
 * <p>
 * The aspect creates and manages gauges that track the number of concurrent
 * executions of annotated methods. Each gauge maintains a count of active
 * method invocations.
 * </p>
 * <p>
 * You can add tags programmatically to the {@link Gauge} using the tags
 * provider function ({@code Function<ProceedingJoinPoint, Iterable<Tag>>}). It
 * receives a {@link ProceedingJoinPoint} and returns the {@link Tag}s that will
 * be attached to the {@link Gauge}.
 * </p>
 * <p>
 * You can also skip the {@link Gauge} creation programmatically using the skip
 * predicate ({@code Predicate<ProceedingJoinPoint>}). This is useful when
 * another component in your application already processes the
 * {@link Gauged @Gauged} annotation in some cases.
 * </p>
 * <p>
 * Example usage to disable gauge creation for specific classes:
 * </p>
 *
 * <pre>
 * &#064;Bean
 * public GaugedAspect gaugedAspect(MeterRegistry meterRegistry) {
 *     return new GaugedAspect(meterRegistry, this::skipSpecificClasses);
 * }
 *
 * private boolean skipSpecificClasses(ProceedingJoinPoint pjp) {
 *     Class<?> targetClass = pjp.getTarget().getClass();
 *     return targetClass.getName().startsWith("com.example.skipped");
 * }
 * </pre>
 *
 * @author Sandeep Vishnu
 */
@Aspect
@NonNullApi
public class GaugedAspect {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(GaugedAspect.class);

    /**
     * Default metric name if none is specified in the annotation.
     */
    private static final String DEFAULT_METRIC_NAME = "method.active.count";

    /**
     * Default predicate that doesn't skip any gauges.
     */
    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    private final MeterRegistry registry;
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;
    private final Predicate<ProceedingJoinPoint> shouldSkip;
    private final Map<String, AtomicInteger> gauges = new ConcurrentHashMap<>();

    /**
     * Creates a {@code GaugedAspect} instance with the given {@code registry}.
     *
     * @param registry Where we're going to register metrics.
     */
    public GaugedAspect(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code GaugedAspect} instance with the given {@code registry} and
     * skip predicate.
     *
     * @param registry   Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the gauge should be
     *                   skipped or not.
     */
    public GaugedAspect(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry,
            pjp -> Tags.of("class",
                pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                "method",
                pjp.getStaticPart().getSignature().getName()),
            shouldSkip);
    }

    /**
     * Creates a {@code GaugedAspect} instance with the given {@code registry}, tags
     * provider function and skip predicate.
     *
     * @param registry             Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip           A predicate to decide if creating the gauge
     *                             should be skipped or not.
     */
    public GaugedAspect(MeterRegistry registry,
                        Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint,
                        Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = makeSafe(tagsBasedOnJoinPoint);
        this.shouldSkip = shouldSkip;
    }

    /**
     * Intercepts classes annotated with {@link Gauged @Gauged} annotation.
     *
     * @param pjp Proceeding join point
     * @return The value from the method execution
     * @throws Throwable When the underlying method throws an exception
     */
    @Around("@within(io.micrometer.core.annotation.Gauged) && !@annotation(io.micrometer.core.annotation.Gauged) && execution(* *(..))")
    @Nullable
    public Object gaugedClass(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Gauged.class)) {
            declaringClass = pjp.getTarget().getClass();
        }
        Gauged gauged = declaringClass.getAnnotation(Gauged.class);
        return processGauge(pjp, gauged);
    }

    /**
     * Intercepts methods annotated with {@link Gauged @Gauged} annotation.
     *
     * @param pjp Proceeding join point
     * @return The value from the method execution
     * @throws Throwable When the underlying method throws an exception
     */
    @Around("@annotation(io.micrometer.core.annotation.Gauged)")
    @Nullable
    public Object gaugedMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Gauged gauged = method.getAnnotation(Gauged.class);
        if (gauged == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            gauged = method.getAnnotation(Gauged.class);
        }
        return processGauge(pjp, gauged);
    }

    /**
     * Processes the method execution with gauge tracking. Increments the gauge
     * before method execution and decrements it after completion.
     *
     * @param pjp    Proceeding join point
     * @param gauged The {@link Gauged} annotation
     * @return The value from the method execution
     * @throws Throwable When the underlying method throws an exception
     */
    private Object processGauge(ProceedingJoinPoint pjp, Gauged gauged) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        String metricName = gauged.value().isEmpty() ? DEFAULT_METRIC_NAME : gauged.value();
        AtomicInteger counter = getOrCreateGauge(pjp, gauged, metricName);

        try {
            counter.incrementAndGet();
            return pjp.proceed();
        }
        finally {
            counter.decrementAndGet();
        }
    }

    /**
     * Gets or creates a gauge for tracking concurrent method executions.
     *
     * @param pjp        Proceeding join point
     * @param gauged     The {@link Gauged} annotation
     * @param metricName Name of the metric
     * @return An {@link AtomicInteger} that tracks the number of concurrent
     *         executions
     */
    private AtomicInteger getOrCreateGauge(ProceedingJoinPoint pjp, Gauged gauged, String metricName) {
        String gaugeKey = generateGaugeKey(metricName, pjp);

        return gauges.computeIfAbsent(gaugeKey, key -> {
            AtomicInteger value = new AtomicInteger(0);

            try {
                Gauge.builder(metricName, value, AtomicInteger::get)
                    .description(gauged.description().isEmpty() ? null : gauged.description())
                    .tags(Tags.concat(Tags.of(gauged.extraTags()), tagsBasedOnJoinPoint.apply(pjp)))
                    .register(registry);
            }
            catch (Exception e) {
                logger.log("Failed to register gauge: " + metricName, e);
            }

            return value;
        });
    }

    /**
     * Generates a unique key for the gauge based on metric name and method
     * signature.
     *
     * @param metricName Name of the metric
     * @param pjp        Proceeding join point
     * @return A unique string key for the gauge
     */
    private String generateGaugeKey(String metricName, ProceedingJoinPoint pjp) {
        return metricName + "_" + pjp.getStaticPart().getSignature().getDeclaringTypeName() + "_"
            + pjp.getStaticPart().getSignature().getName();
    }

    /**
     * Creates a safe version of the tags provider function that won't throw
     * exceptions.
     *
     * @param function The original tags provider function
     * @return A safe version of the function that returns empty tags on error
     */
    private Function<ProceedingJoinPoint, Iterable<Tag>> makeSafe(Function<ProceedingJoinPoint, Iterable<Tag>> function) {
        return pjp -> {
            try {
                return function.apply(pjp);
            }
            catch (Exception t) {
                logger.log("Exception thrown from the tagsBasedOnJoinPoint function configured on GaugedAspect.", t);
                return Tags.empty();
            }
        };
    }
}

