/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.util.internal.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Simple {@link InternalLoggerFactory} implementation that always returns an instance of {@link InternalMockLogger}
 * so components that fetch the logger this way: <code>InternalLogger logger = InternalLoggerFactory.getInstance(MyClass.class);</code>,
 * get a {@link InternalMockLogger} instance if they are created using <code>factory.injectLogger(MyClass::new)</code>
 * or if <code>InternalLoggerFactory.setDefaultFactory(internalMockLoggerFactory);</code> was set previously.
 *
 * @author Jonatan Ivanov
 */
public class InternalMockLoggerFactory extends InternalLoggerFactory {
    private final ConcurrentHashMap<String, InternalMockLogger> loggers = new ConcurrentHashMap<>();

    /**
     * A convenient way to create a logger. In contrast of getInstance, this method returns a {@link InternalMockLogger}
     * instead of a {@link InternalLoggerFactory} so that you don't need to cast.
     *
     * @param clazz The class that you need the logger for.
     * @return An instance of {@link InternalMockLogger} for the provided class.
     */
    public InternalMockLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * A convenient way to create a logger. In contrast of getInstance, this method returns a {@link InternalMockLogger}
     * instead of a {@link InternalLoggerFactory} so that you don't need to cast.
     *
     * @param name The name of the logger.
     * @return An instance of {@link InternalMockLogger} for the provided name.
     */
    public InternalMockLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, InternalMockLogger::new);
    }

    /**
     * A factory method that returns the result of the given Supplier and it also injects the default factory.
     * So if the logger is created this way: <code>InternalLogger logger = InternalLoggerFactory.getInstance(MyClass.class);</code>,
     * the resulted logger will be a {@link InternalMockLogger}.
     *
     * @param supplier The logic that creates the object you need to inject the logger into.
     * @return The result of the Supplier.
     */
    public <T> T injectLogger(Supplier<T> supplier) {
        synchronized (this) {
            InternalLoggerFactory original = getDefaultFactory();
            setDefaultFactory(this);
            T object = supplier.get();
            setDefaultFactory(original);

            return object;
        }
    }

    @Override
    protected InternalLogger newInstance(String name) {
        return getLogger(name);
    }
}
