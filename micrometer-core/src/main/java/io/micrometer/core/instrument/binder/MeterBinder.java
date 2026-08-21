/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Binders register one or more metrics to provide information about the state of some
 * aspect of the application or its container.
 * <p>
 * Binders are enabled by default if they source data for an alert that is recommended for
 * a production ready app.
 *
 * <h2>Lifecycle</h2>
 * {@link #bindTo(MeterRegistry)} is typically called once per registry during application
 * startup (configuration time). The same {@code MeterBinder} instance may be bound to
 * multiple registries, for example when a composite registry and an individual registry
 * are both present in a Spring Boot application. Each such call registers fresh meters on
 * the supplied registry.
 *
 * <h2>Thread Safety</h2>
 * Implementations are <strong>not</strong> required to make {@link #bindTo} thread-safe.
 * Concurrent invocations of {@link #bindTo} on the same instance, or invocations that
 * overlap with ongoing meter observations from a previous call, are not a supported use
 * case. In normal usage, all {@code bindTo} calls for a given instance happen serially
 * during the single-threaded application context initialization phase.
 */
public interface MeterBinder {

    void bindTo(MeterRegistry registry);

}
