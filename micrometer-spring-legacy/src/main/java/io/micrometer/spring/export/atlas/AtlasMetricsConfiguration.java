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

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.core.instrument.Clock;
import io.micrometer.atlas.AtlasMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * @author Jon Schneider
 */
@Configuration
public class AtlasMetricsConfiguration {
    @Bean
    AtlasMeterRegistry meterRegistry(AtlasConfig atlasConfig, Clock clock) {
        return new AtlasMeterRegistry(atlasConfig, clock);
    }

    @Bean
    AtlasConfig atlasConfig(Environment environment) {
        return environment::getProperty;
    }

    @ConditionalOnMissingBean
    @Bean
    Clock clock() {
        return Clock.SYSTEM;
    }
}
