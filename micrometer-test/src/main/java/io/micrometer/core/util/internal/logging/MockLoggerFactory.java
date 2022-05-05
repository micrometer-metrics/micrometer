/*
 * Copyright 2021 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.util.internal.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Simple {@link InternalLoggerFactory} implementation that always returns an instance of {@link MockLogger}
 * so components that fetch the logger this way: <code>InternalLogger logger = InternalLoggerFactory.getInstance(MyClass.class);</code>,
 * get a {@link MockLogger} instance if they are created using <code>factory.injectLogger(MyClass::new)</code>
 * or if <code>InternalLoggerFactory.setDefaultFactory(mockLoggerFactory);</code> was set previously.
 *
 * @author Jonatan Ivanov
 *
 * @deprecated Please use {@link MockLoggerFactory} instead.
 */
@Deprecated
public class MockLoggerFactory extends InternalLoggerFactory {
    private final ConcurrentMap<String, MockLogger> loggers = new ConcurrentHashMap<>();

    /**
     * A convenient way to create a logger. In contrast of {@link #getInstance(Class)}, this method returns a {@link MockLogger}
     * instead of an {@link InternalLoggerFactory} so that you don't need to cast.
     *
     * @param clazz The class that you need the logger for.
     * @return An instance of {@link MockLogger} for the provided class.
     */
    public MockLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * A convenient way to create a logger. In contrast of {@link #getInstance(String)}, this method returns a {@link MockLogger}
     * instead of an {@link InternalLoggerFactory} so that you don't need to cast.
     *
     * @param name The name of the logger.
     * @return An instance of {@link MockLogger} for the provided name.
     */
    public MockLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, MockLogger::new);
    }

    /**
     * A factory method that returns the result of the given {@code Supplier} and it also injects the default factory.
     * So if the logger is created this way: <code>InternalLogger logger = InternalLoggerFactory.getInstance(MyClass.class);</code>,
     * the resulted logger will be a {@link MockLogger}.
     *
     * @param supplier The logic that creates the object you need to inject the logger into.
     * @param <T> The type of an object that will be created by the {@code Supplier}.
     * @return The result of the {@code Supplier}.
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
