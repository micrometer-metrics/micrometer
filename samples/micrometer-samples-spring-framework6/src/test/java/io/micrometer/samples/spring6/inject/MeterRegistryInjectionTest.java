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
package io.micrometer.samples.spring6.inject;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demonstrates the combination of meter registry field injection + lazy meter fields.
 *
 * @author Jon Schneider
 */
class MeterRegistryInjectionTest {

    @Test
    void injectWithSpring() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                SpringConfiguration.class)) {
            MyComponent component = ctx.getBean(MyComponent.class);
            component.performanceCriticalFeature();
            assertThat(component.registry).isInstanceOf(SimpleMeterRegistry.class);
            component.registry.get("feature.counter").counter();
        }
    }

    @Test
    void noInjection() {
        MyComponent component = new MyComponent();
        assertThatThrownBy(component::performanceCriticalFeature).isInstanceOf(NullPointerException.class);
    }

}

@Configuration
class SpringConfiguration {

    @Bean
    SimpleMeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    MyComponent component() {
        return new MyComponent();
    }

}

class MyComponent {

    @Nullable
    @Inject
    MeterRegistry registry;

    // for performance-critical uses, it is best to store a meter in a field
    @Nullable
    Counter counter;

    @Inject
    MyComponent() {
    }

    @PostConstruct
    void after() {
        counter = requireNonNull(registry).counter("feature.counter");
    }

    void performanceCriticalFeature() {
        requireNonNull(counter).increment();
    }

    void notPerformanceCriticalFeature() {
        // in code blocks that are not performance-critical, it is acceptable to inline
        // the retrieval of the counter
        Metrics.counter("infrequent.counter").increment();
    }

}
