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
package io.micrometer.core.instrument;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

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
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfiguration.class);
        MyComponent component = ctx.getBean(MyComponent.class);
        component.performanceCriticalFeature();
        assertThat(component.registry).isInstanceOf(SimpleMeterRegistry.class);
        component.registry.mustFind("feature.counter").counter();
    }

//    @Test
//    void injectWithDagger() {
//        DagConfiguration conf = DaggerDagConfiguration.create();
//        MyComponent component = conf.component();
//        component.after(); // @PostConstruct is not automatically called
//        component.performanceCriticalFeature();
//        assertThat(component.registry)
//                .isInstanceOf(SimpleMeterRegistry.class)
//                .matches(r -> r.find("feature.counter").counter().isPresent());
//    }

    @Test
    void injectWithGuice() {
        Injector injector = Guice.createInjector(new GuiceConfiguration());
        MyComponent component = injector.getInstance(MyComponent.class);
        component.after(); // @PostConstruct is not automatically called
        component.performanceCriticalFeature();
        assertThat(component.registry).isInstanceOf(SimpleMeterRegistry.class);
        component.registry.mustFind("feature.counter").counter();
    }

    @Test
    void noInjection() {
        MyComponent component = new MyComponent();
        assertThatThrownBy(component::performanceCriticalFeature)
            .isInstanceOf(NullPointerException.class);
    }
}

//@Component(modules = DagConfiguration.RegistryConf.class)
//interface DagConfiguration {
//    MyComponent component();
//
//    @Module
//    class RegistryConf {
//        @Provides
//        static MeterRegistry registry() {
//            return new SimpleMeterRegistry();
//        }
//    }
//}

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

class GuiceConfiguration extends AbstractModule {
    @Override
    protected void configure() {
        bind(MeterRegistry.class).to(SimpleMeterRegistry.class);
    }
}

class MyComponent {
    @Inject MeterRegistry registry;

    // for performance-critical uses, it is best to store a meter in a field
    Counter counter;

    @PostConstruct
    public void after() {
        counter = registry.counter("feature.counter");
    }

    void performanceCriticalFeature() {
        counter.increment();
    }

    void notPerformanceCriticalFeature() {
        // in code blocks that are not performance-critical, it is acceptable to inline
        // the retrieval of the counter
        Metrics.counter("infrequent.counter").increment();
    }

    @Inject MyComponent() {}
}
