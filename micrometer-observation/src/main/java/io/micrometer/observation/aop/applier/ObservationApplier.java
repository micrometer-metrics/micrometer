package io.micrometer.observation.aop.applier;

import io.micrometer.common.lang.NonNull;
import io.micrometer.observation.Observation;
import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;

public interface ObservationApplier {

    boolean isApplicable(@NonNull ProceedingJoinPoint pjp, @NonNull Method method);

    Object applyAndProceed(
        @NonNull ProceedingJoinPoint pjp,
        @NonNull Method method,
        @NonNull Observation observation
    ) throws Throwable;
}
