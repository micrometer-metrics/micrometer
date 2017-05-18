package org.springframework.metrics.instrument;

@FunctionalInterface
public interface ThrowableCallable<V> {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Throwable if unable to compute a result
     */
    V call() throws Throwable;
}
