package io.micrometer.observation.aop;

import java.lang.reflect.Method;

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
                .lowCardinalityKeyValue("method", signature.getName())
                .observeChecked(() -> pjp.proceed());
    }
}
