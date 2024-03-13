/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    public Object applyAndProceed(@NonNull ProceedingJoinPoint pjp, @NonNull Method method,
            @NonNull Observation observation) throws Throwable {
        observation.start();
        Observation.Scope scope = observation.openScope();
        try {
            return ((CompletionStage<?>) pjp.proceed())
                .whenComplete((result, error) -> stopObservation(observation, scope, error));
        }
        catch (Throwable error) {
            stopObservation(observation, scope, error);
            throw error;
        }
        finally {
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
