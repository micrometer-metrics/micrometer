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

/**
 * Simple {@link InternalLoggerFactory} implementation that always returns an instance of {@link InternalMockLogger}
 * so that components that are fetching the logger this way: <code>InternalLogger logger = InternalLoggerFactory.getInstance(...);</code>
 * will get a {@link InternalMockLogger} instance if <code>InternalLoggerFactory.setDefaultFactory(new InternalMockLoggerFactory());</code> was set previously.
 *
 * @author Jonatan Ivanov
 */
public class InternalMockLoggerFactory extends InternalLoggerFactory {
    private final ConcurrentHashMap<String, InternalLogger> loggers = new ConcurrentHashMap<>();

    @Override
    protected InternalLogger newInstance(String name) {
        return loggers.computeIfAbsent(name, InternalMockLogger::new);
    }
}
