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

import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObservationApplierRegistry {

    private static ObservationApplierRegistry instance;

    private final List<ObservationApplier> observationAppliers;

    public ObservationApplierRegistry(List<ObservationApplier> observationAppliers) {
        this.observationAppliers = new CopyOnWriteArrayList<>(observationAppliers);
    }

    public Optional<ObservationApplier> findApplicable(ProceedingJoinPoint pjp, Method method) {
        for (ObservationApplier observationApplier : observationAppliers) {
            if (observationApplier.isApplicable(pjp, method)) {
                return Optional.of(observationApplier);
            }
        }
        return Optional.empty();
    }

    public void register(ObservationApplier observationApplier) {
        this.observationAppliers.add(observationApplier);
    }

    public static ObservationApplierRegistry getInstance() {
        if (instance == null) {
            instance = new ObservationApplierRegistry(
                    Collections.singletonList(new ObservationToCompletionStageApplier()));
        }
        return instance;
    }

}
