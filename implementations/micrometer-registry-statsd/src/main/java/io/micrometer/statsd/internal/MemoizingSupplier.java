/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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