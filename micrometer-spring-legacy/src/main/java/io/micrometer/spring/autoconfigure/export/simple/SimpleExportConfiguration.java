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
package io.micrometer.spring.autoconfigure.export.simple;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for exporting metrics to a {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
@Configuration
@EnableConfigurationProperties(SimpleProperties.class)
public class SimpleExportConfiguration {

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.simple.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(MetricsExporter.class)
    public MetricsExporter simpleExporter(Clock clock) {
        return () -> new SimpleMeterRegistry(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.SYSTEM;
    }

}
