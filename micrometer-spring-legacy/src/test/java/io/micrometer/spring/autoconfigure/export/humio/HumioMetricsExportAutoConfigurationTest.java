/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure.export.humio;

import io.micrometer.core.instrument.Clock;
import io.micrometer.humio.HumioConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HumioMetricsExportAutoConfiguration}.
 */
class HumioMetricsExportAutoConfigurationTest {

    private AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @Test
    void anyAdditionToTagsReplacesDefault() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.humio.tags.app=myapp");
        registerAndRefresh(ClockConfiguration.class, HumioMetricsExportAutoConfiguration.class);
        assertThat(context.getBean(HumioConfig.class).tags()).containsOnlyKeys("app");
    }

    @Test
    void defaultTag() {
        registerAndRefresh(ClockConfiguration.class, HumioMetricsExportAutoConfiguration.class);
        assertThat(context.getBean(HumioConfig.class).tags()).isEmpty();
    }

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        context.register(configurationClasses);
        context.refresh();
    }

    @Configuration
    static class ClockConfiguration {
        @Bean
        public Clock clock() {
            return Clock.SYSTEM;
        }

    }
}
