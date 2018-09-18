/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure;

import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MetricsAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Johnny Lim
 */
class MetricsAutoConfigurationIntegrationTest {

    private AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void definesTagsProviderAndFilterWhenMeterRegistryIsPresent() {
        prepareEnvironment("management.metrics.tags.region=test",
                "management.metrics.tags.origin=local");
        registerAndRefresh(MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class);

        MeterRegistry registry = this.context.getBean(MeterRegistry.class);
        registry.counter("my.counter", "env", "qa");
        assertThat(registry.find("my.counter").tags("env", "qa")
                .tags("region", "test").tags("origin", "local").counter())
                .isNotNull();
    }

    private void prepareEnvironment(String... properties) {
        EnvironmentTestUtils.addEnvironment(this.context, properties);
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        this.context.register(configurationClasses);
        this.context.refresh();
    }

}
