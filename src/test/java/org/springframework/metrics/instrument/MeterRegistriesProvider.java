/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument;

import com.netflix.spectator.api.DefaultRegistry;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.extension.ContainerExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ObjectArrayArguments;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

import java.util.stream.Stream;

class MeterRegistriesProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> arguments(ContainerExtensionContext context) throws Exception {
        return Stream.of(
                new SpectatorMeterRegistry(new DefaultRegistry(), new MockClock()),
                new PrometheusMeterRegistry(new CollectorRegistry(true), new MockClock()),
                new SimpleMeterRegistry(new MockClock())
        ).map(ObjectArrayArguments::create);
    }
}
