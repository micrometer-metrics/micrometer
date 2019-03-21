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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNullApi;

/**
 * Callback interface that can be used to customize the primary auto-configured {@link MeterRegistry}.
 * <p>
 * Configurers are guaranteed to be applied before any {@link Meter} is registered with
 * the registry.
 *
 * @author Jon Schneider
 */
@FunctionalInterface
@NonNullApi
public interface MeterRegistryCustomizer<M extends MeterRegistry> {

    /**
     * Configure the given registry.
     *
     * @param registry the registry to configure
     */
    void customize(M registry);
}
