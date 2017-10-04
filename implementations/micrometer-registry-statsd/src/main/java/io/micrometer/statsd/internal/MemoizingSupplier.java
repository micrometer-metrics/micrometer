package io.micrometer.statsd.internal;

import java.util.function.Supplier;

/**
 * Modified from Guava's MemoizingSupplier
 * @param <T>
 */
public class MemoizingSupplier<T> implements Supplier<T> {

    final Supplier<T> delegate;
    transient volatile boolean initialized;

    // "value" does not need to be volatile; visibility piggy-backs
    // on volatile read of "initialized".
    transient T value;

    public MemoizingSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    public static <U> MemoizingSupplier<U> memoize(Supplier<U> delegate) {
        return new MemoizingSupplier<>(delegate);
    }

    @Override
    public T get() {
        // A 2-field variant of Double Checked Locking.
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    T t = delegate.get();
                    value = t;
                    initialized = true;
                    return t;
                }
            }
        }
        return value;
    }

    @Override
    public String toString() {
        return "Suppliers.memoize(" + delegate + ")";
    }
}