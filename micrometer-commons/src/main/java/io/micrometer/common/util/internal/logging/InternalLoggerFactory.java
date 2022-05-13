/*
 * Copyright 2019 VMware, Inc.
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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.micrometer.common.util.internal.logging;

import static java.util.Objects.requireNonNull;

/**
 * NOTE: This file has been copied and simplified from {io.netty.util.internal.logging}.
 *
 * Creates an {@link InternalLogger} or changes the default factory implementation. This
 * factory allows you to choose what logging framework Micrometer should use. The default
 * factory is {@link Slf4JLoggerFactory}. If SLF4J is not available,
 * {@link JdkLoggerFactory} is used. You can change it to your preferred logging framework
 * before other Micrometer classes are loaded: <pre>
 * {@link InternalLoggerFactory}.setDefaultFactory({@link JdkLoggerFactory}.INSTANCE);
 * </pre> Please note that the new default factory is effective only for the classes which
 * were loaded after the default factory is changed. Therefore,
 * {@link #setDefaultFactory(InternalLoggerFactory)} should be called as early as possible
 * and shouldn't be called more than once.
 */
public abstract class InternalLoggerFactory {

    private static volatile InternalLoggerFactory defaultFactory;

    @SuppressWarnings("UnusedCatchParameter")
    private static InternalLoggerFactory newDefaultFactory(String name) {
        InternalLoggerFactory f;
        try {
            f = Slf4JLoggerFactory.INSTANCE;
            f.newInstance(name).debug("Using SLF4J as the default logging framework");
        }
        catch (Throwable ignored) {
            f = JdkLoggerFactory.INSTANCE;
            f.newInstance(name).debug("Using java.util.logging as the default logging framework");
        }
        return f;
    }

    /**
     * Returns the default factory.
     * @return default factory
     */
    public static InternalLoggerFactory getDefaultFactory() {
        if (defaultFactory == null) {
            defaultFactory = newDefaultFactory(InternalLoggerFactory.class.getName());
        }
        return defaultFactory;
    }

    /**
     * Changes the default factory.
     * @param defaultFactory default factory
     */
    public static void setDefaultFactory(InternalLoggerFactory defaultFactory) {
        requireNonNull(defaultFactory, "defaultFactory");
        InternalLoggerFactory.defaultFactory = defaultFactory;
    }

    /**
     * Creates a new logger instance with the name of the specified class.
     * @param clazz class to use for a logger name
     * @return logger instance
     */
    public static InternalLogger getInstance(Class<?> clazz) {
        return getInstance(clazz.getName());
    }

    /**
     * Creates a new logger instance with the specified name.
     * @param name logger name
     * @return logger instance
     */
    public static InternalLogger getInstance(String name) {
        return getDefaultFactory().newInstance(name);
    }

    /**
     * Creates a new logger instance with the specified name.
     * @param name logger name
     * @return logger instance
     */
    protected abstract InternalLogger newInstance(String name);

}
