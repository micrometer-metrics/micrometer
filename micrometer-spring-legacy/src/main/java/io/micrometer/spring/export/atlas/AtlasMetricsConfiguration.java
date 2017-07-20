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
package io.micrometer.spring.export.atlas;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * @author Jon Schneider
 */
@Configuration
public class AtlasMetricsConfiguration {
    @Bean
    AtlasTagFormatter tagFormatter() {
        return new AtlasTagFormatter();
    }

    @Bean
    SpectatorMeterRegistry meterRegistry(Registry registry) {
        return new SpectatorMeterRegistry(registry);
    }

    @Bean
    AtlasConfig atlasConfig(Environment environment) {
        return environment::getProperty;
    }

    @Bean
    AtlasRegistry atlasRegistry(AtlasConfig atlasConfig) {
        AtlasRegistry registry = new AtlasRegistry(Clock.SYSTEM, atlasConfig);
        registry.start();
        return registry;
    }
}
