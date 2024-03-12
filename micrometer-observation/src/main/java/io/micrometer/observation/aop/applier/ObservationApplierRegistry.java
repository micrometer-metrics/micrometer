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
                Collections.singletonList(new ObservationToCompletionStageApplier())
            );
        }
        return instance;
    }
}
