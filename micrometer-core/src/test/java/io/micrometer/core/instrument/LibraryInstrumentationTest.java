package io.micrometer.core.instrument;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

import static io.micrometer.core.instrument.LazyCounter.lazyCounter;
import static org.assertj.core.api.Assertions.assertThat;

class LibraryInstrumentationTest {
    @Test
    void injectWithSpring() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfiguration.class);
        MyComponent component = ctx.getBean(MyComponent.class);
        component.feature();
        assertThat(component.registry)
                .isInstanceOf(PrometheusMeterRegistry.class)
                .matches(r -> r.findMeter(Meter.Type.Counter, "feature_counter").isPresent());
    }

    @Test
    void injectWithDagger() {
        DagConfiguration conf = DaggerDagConfiguration.create();
        MyComponent component = conf.component();
        component.feature();
        assertThat(component.registry)
                .isInstanceOf(PrometheusMeterRegistry.class)
                .matches(r -> r.findMeter(Meter.Type.Counter, "feature_counter").isPresent());
    }

    @Test
    void injectWithGuice() {
        Injector injector = Guice.createInjector(new GuiceConfiguration());
        MyComponent component = injector.getInstance(MyComponent.class);
        component.feature();
        assertThat(component.registry)
                .isInstanceOf(PrometheusMeterRegistry.class)
                .matches(r -> r.findMeter(Meter.Type.Counter, "feature_counter").isPresent());
    }

    @Test
    void noInjection() {
        MyComponent component = new MyComponent();
        component.feature();
        assertThat(component.registry)
                .isInstanceOf(SimpleMeterRegistry.class)
                .matches(r -> r.findMeter(Meter.Type.Counter, "feature_counter").isPresent());
    }
}

@Component(modules = DagConfiguration.RegistryConf.class)
interface DagConfiguration {
    MyComponent component();

    @Module
    class RegistryConf {
        @Provides
        static MeterRegistry registry() {
            return new PrometheusMeterRegistry(new CollectorRegistry());
        }
    }
}

@Configuration
class SpringConfiguration {
    @Bean
    PrometheusMeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(new CollectorRegistry());
    }

    @Bean
    MyComponent component() {
        return new MyComponent();
    }
}

class GuiceConfiguration extends AbstractModule {
    @Override
    protected void configure() {
        bind(MeterRegistry.class).to(PrometheusMeterRegistry.class);
    }
}

class MyComponent {
    @Inject
    MeterRegistry registry = new SimpleMeterRegistry();
    Counter counter = lazyCounter(() -> registry.counter("feature_counter"));

    void feature() {
        counter.increment();
    }

    // required for dagger, not related to micrometer
    @Inject MyComponent() {}
}

final class LazyCounter implements Counter {
    public static Counter lazyCounter(Supplier<Counter> counterBuilder) {
        return new LazyCounter(counterBuilder);
    }

    private final Supplier<Counter> counterBuilder;
    private volatile Counter counter;

    private Counter counter() {
        final Counter result = counter;
        return result == null ? (counter == null ? counterBuilder.get() : counter) : result;
    }

    private LazyCounter(Supplier<Counter> counterBuilder) {
        this.counterBuilder = counterBuilder;
    }

    @Override
    public String getName() {
        return counter().getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return counter().getTags();
    }

    @Override
    public List<Measurement> measure() {
        return counter().measure();
    }

    @Override
    public void increment(double amount) {
        counter().increment();
    }

    @Override
    public double count() {
        return counter().count();
    }
}
