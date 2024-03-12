package io.micrometer.observation.aop.applier;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

public class ObservationToCompletionStageApplier implements ObservationApplier {
    @Override
    public boolean isApplicable(@NonNull ProceedingJoinPoint pjp, @NonNull Method method) {
        return CompletionStage.class.isAssignableFrom(method.getReturnType());
    }

    @Override
    public Object applyAndProceed(
        @NonNull ProceedingJoinPoint pjp,
        @NonNull Method method,
        @NonNull Observation observation
    ) throws Throwable {
        observation.start();
        Observation.Scope scope = observation.openScope();
        try {
            return ((CompletionStage<?>) pjp.proceed())
                .whenComplete((result, error) -> stopObservation(observation, scope, error));
        } catch (Throwable error) {
            stopObservation(observation, scope, error);
            throw error;
        } finally {
            scope.close();
        }
    }

    private void stopObservation(Observation observation, Observation.Scope scope, @Nullable Throwable error) {
        if (error != null) {
            observation.error(error);
        }
        scope.close();
        observation.stop();
    }
}
