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
package io.micrometer.spring.autoconfigure.export.graphite;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteHierarchicalNameMapper;
import io.micrometer.graphite.GraphiteMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GraphiteMetricsExportAutoConfiguration}.
 *
 * @author Johnny Lim
 */
class GraphiteMetricsExportAutoConfigurationTest {

    private AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    public void autoConfigureGraphiteHierarchicalNameMapper() {
        registerAndRefresh(BaseConfiguration.class, GraphiteMetricsExportAutoConfiguration.class);

        assertThat(this.context.getBean(HierarchicalNameMapper.class))
                .isInstanceOf(GraphiteHierarchicalNameMapper.class);
    }

    @Test
    public void backOffGraphiteHierarchicalNameMapperWhenAlreadyDefined() {
        registerAndRefresh(BaseConfiguration.class, HierarchicalNameMapperConfiguration.class,
                GraphiteMetricsExportAutoConfiguration.class);

        assertThat(this.context.getBean(HierarchicalNameMapper.class))
            .isSameAs(this.context.getBean("customHierarchicalNameMapper"));
    }

    @Test
    public void graphiteMeterRegistryUsesUserProvidedHierarchicalNameMapper() {
        registerAndRefresh(BaseConfiguration.class, HierarchicalNameMapperConfiguration.class,
                GraphiteMetricsExportAutoConfiguration.class);

        GraphiteMeterRegistry meterRegistry = this.context.getBean(GraphiteMeterRegistry.class);
        DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(meterRegistry);
        assertThat(fieldAccessor.getPropertyValue("nameMapper"))
                .isSameAs(this.context.getBean("customHierarchicalNameMapper"));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        this.context.register(configurationClasses);
        this.context.refresh();
    }

    @Configuration
    static class BaseConfiguration {

        @Bean
        public Clock clock() {
            return mock(Clock.class);
        }

    }

    @Configuration
    static class HierarchicalNameMapperConfiguration {

        @Bean
        public HierarchicalNameMapper customHierarchicalNameMapper() {
            return mock(HierarchicalNameMapper.class);
        }

    }

}
