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
package io.micrometer.core.instrument;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demonstrates the combination of meter registry field injection + lazy meter fields.
 *
 * @author Jon Schneider
 */
class MeterRegistryInjectionTest {

    // @Test
    // void injectWithDagger() {
    // DagConfiguration conf = DaggerDagConfiguration.create();
    // MyComponent component = conf.component();
    // component.after(); // @PostConstruct is not automatically called
    // component.performanceCriticalFeature();
    // assertThat(component.registry)
    // .isInstanceOf(SimpleMeterRegistry.class)
    // .matches(r -> r.find("feature.counter").counter().isPresent());
    // }

    @Test
    void injectWithGuice() {
        Injector injector = Guice.createInjector(new GuiceConfiguration());
        MyComponent component = injector.getInstance(MyComponent.class);
        component.after(); // @PostConstruct is not automatically called
        component.performanceCriticalFeature();
        assertThat(component.registry).isInstanceOf(SimpleMeterRegistry.class);
        component.registry.get("feature.counter").counter();
    }

    @Test
    void noInjection() {
        MyComponent component = new MyComponent();
        assertThatThrownBy(component::performanceCriticalFeature).isInstanceOf(NullPointerException.class);
    }

}

// @Component(modules = DagConfiguration.RegistryConf.class)
// interface DagConfiguration {
// MyComponent component();
//
// @Module
// class RegistryConf {
// @Provides
// static MeterRegistry registry() {
// return new SimpleMeterRegistry();
// }
// }
// }

class GuiceConfiguration extends AbstractModule {

    @Override
    protected void configure() {
        bind(MeterRegistry.class).to(SimpleMeterRegistry.class);
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
