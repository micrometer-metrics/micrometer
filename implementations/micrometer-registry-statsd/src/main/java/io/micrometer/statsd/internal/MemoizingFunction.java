/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd.internal;

import java.util.function.Function;

/**
 * Modified from Guava's MemoizingFunction
 *
 * @param <T> The domain type.
 * @param <R> The range type.
 */
public class MemoizingFunction<T, R> implements Function<T, R> {

    private final Function<T, R> delegate;
    private transient volatile boolean initialized;
    private transient volatile T lastInput;

    // "value" does not need to be volatile; visibility piggy-backs
    // on volatile read of "initialized".
    private transient R value;

    public MemoizingFunction(Function<T, R> delegate) {
        this.delegate = delegate;
    }

    public static <U, V> MemoizingFunction<U, V> memoize(Function<U, V> delegate) {
        return new MemoizingFunction<>(delegate);
    }

    @Override
    public R apply(T t) {
        if (!initialized || t != lastInput) {
            synchronized (this) {
                if (!initialized || t != lastInput) {
                    lastInput = t;
                    R r = delegate.apply(t);
                    value = r;
                    initialized = true;
                    return r;
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