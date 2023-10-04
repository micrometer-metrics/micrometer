/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation.Context;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @author Yanming Zhou
 */
class ObservationTests {

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        this.registry = ObservationRegistry.create();
    }

    @Test
    void notHavingAnyHandlersShouldResultInNoopObservation() {
        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void notHavingARegistryShouldResultInNoopObservation() {
        Observation observation = Observation.createNotStarted("foo", null);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void notMatchingObservationPredicateShouldResultInNoopObservation() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationPredicate((s, context) -> false);

        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void matchingPredicateAndHandlerShouldNotResultInNoopObservation() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationPredicate((s, context) -> true);

        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isNotSameAs(Observation.NOOP);
    }

    @Test
    void usingParentObservationToMatchPredicate() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig()
            .observationPredicate((s, context) -> !s.equals("child") || context.getParentObservation() != null);

        Observation childWithoutParent = Observation.createNotStarted("child", registry);
        assertThat(childWithoutParent).isSameAs(Observation.NOOP);

        Observation childWithParent = Observation.createNotStarted("parent", registry)
            .observe(() -> Observation.createNotStarted("child", registry));
        assertThat(childWithParent).isNotSameAs(Observation.NOOP);
    }

    @Test
    void havingAnObservationFilterWillMutateTheContext() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationFilter(context -> context.put("foo", "bar"));
        Observation.Context context = new Observation.Context();

        Observation.start("foo", () -> context, registry).stop();

        assertThat((String) context.get("foo")).isEqualTo("bar");
    }

    @Test
    void havingAnObservationFilterToModifyKeyValue() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("foo", "FOO"));

        ObservationFilter filter = (ctx) -> {
            KeyValue keyValue = ctx.getHighCardinalityKeyValue("foo");
            assertThat(keyValue).isNotNull();
            return ctx.addHighCardinalityKeyValue(KeyValue.of(keyValue.getKey(), keyValue.getValue() + "-modified"));
        };

        registry.observationConfig().observationFilter(filter);

        Observation.start("foo", () -> context, registry).stop();

        assertThat(context.getHighCardinalityKeyValue("foo")).isNotNull()
            .extracting(KeyValue::getValue)
            .isEqualTo("FOO-modified");
    }

    @Test
    void settingParentObservationMakesAReferenceOnParentContext() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context parentContext = new Observation.Context();
        Observation parent = Observation.start("parent", () -> parentContext, registry);

        Observation.Context childContext = new Observation.Context();
        Observation child = Observation.createNotStarted("child", () -> childContext, registry)
            .parentObservation(parent)
            .start();

        parent.stop();
        child.stop();

        assertThat(child.getContextView()).isSameAs(childContext);
        assertThat(parent.getContextView()).isSameAs(parentContext);

        assertThat(childContext.getParentObservation().getContextView()).isSameAs(parentContext);
    }

    @Test
    void creatingAChildInAScopeLinksTheTwoAsParentAndChild() {
        registry.observationConfig().observationHandler(context -> true);

        Observation parent = Observation.start("parent", registry);
        Observation parent2 = Observation.start("parent2", registry);
        Observation childOutsideOfScope = Observation.createNotStarted("childOutsideOfScope", registry)
            .parentObservation(parent2)
            .start();
        parent.scoped(() -> {
            Observation child = Observation.start("child", registry);
            assertThat(child.getContextView().getParentObservation()).isSameAs(parent);
            assertThat(childOutsideOfScope.getContextView().getParentObservation()).isSameAs(parent2);
        });
        parent.stop();
    }

    @Test
    void scopedCheckedSpecificException() {
        Service service = new Service();
        Observation observation = Observation.start("service", registry);
        try {
            String s = observation.scopedChecked(service::executeCallable);
            observation.scopedChecked(service::executeRunnable);
        }
        catch (IOException ignore) {
        }
    }

    @Test
    void customAndDefaultAndNoGlobalConventionShouldResolveToCustomConvention() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(new CustomConvention(), new DefaultConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("custom.name");
        assertThat(context.getContextualName()).isEqualTo("custom.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("custom", "present"), KeyValue.of("low", "custom"));
    }

    @Test
    void customAndDefaultAndGlobalConventionShouldResolveToCustomConvention() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationConvention(new GlobalConvention());

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(new CustomConvention(), new DefaultConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("custom.name");
        assertThat(context.getContextualName()).isEqualTo("custom.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("custom", "present"), KeyValue.of("low", "custom"));
    }

    @Test
    void noCustomAndDefaultAndNoGlobalConventionShouldResolveToDefaultConvention() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(null, new DefaultConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("default.name");
        assertThat(context.getContextualName()).isEqualTo("default.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("default", "present"), KeyValue.of("low", "default"));
    }

    @Test
    void noCustomAndDefaultAndGlobalConventionShouldResolveToGlobalConvention() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationConvention(new GlobalConvention());

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(null, new DefaultConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("global.name");
        assertThat(context.getContextualName()).isEqualTo("global.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("global", "present"), KeyValue.of("low", "global"));
    }

    @Test
    void customAndNoDefaultAndNoGlobalConventionShouldResolveToCustomConvention() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(new CustomConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("custom.name");
        assertThat(context.getContextualName()).isEqualTo("custom.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("custom", "present"), KeyValue.of("low", "custom"));
    }

    @Test
    void customAndNoDefaultAndGlobalConventionShouldResolveToCustomConvention() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationConvention(new GlobalConvention());

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(new CustomConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("custom.name");
        assertThat(context.getContextualName()).isEqualTo("custom.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("custom", "present"), KeyValue.of("low", "custom"));
    }

    @Test
    void noCustomAndNoDefaultAndNoGlobalConventionShouldResolveToNoConvention() {
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted("local.name", () -> context, registry)
            .contextualName("local.contextualName")
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("local.name");
        assertThat(context.getContextualName()).isEqualTo("local.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("low", "local"));
    }

    @Test
    void noCustomAndNoDefaultAndGlobalConventionShouldResolveToGlobalConvention() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationConvention(new GlobalConvention());

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted("local.name", () -> context, registry)
            .contextualName("local.contextualName")
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("global.name");
        assertThat(context.getContextualName()).isEqualTo("global.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("global", "present"), KeyValue.of("low", "global"));
    }

    @Test
    void instrumentationAndCustomAndDefaultAndGlobalConventionShouldResolveToInstrumentationConvention() {
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationConvention(new GlobalConvention());

        Observation.Context context = new Observation.Context();
        Observation.createNotStarted(new CustomConvention(), new DefaultConvention(), () -> context, registry)
            .lowCardinalityKeyValue("local", "present")
            .lowCardinalityKeyValue("low", "local")
            .observationConvention(new InstrumentationConvention())
            .start()
            .stop();

        assertThat(context.getName()).isEqualTo("instrumentation.name");
        assertThat(context.getContextualName()).isEqualTo("instrumentation.contextualName");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("local", "present"),
                KeyValue.of("instrumentation", "present"), KeyValue.of("low", "instrumentation"));
    }

    @Test
    void contextShouldWorkOnMultipleThreads() {
        Observation.Context context = new Observation.Context();
        AtomicBoolean exception = new AtomicBoolean();

        new Thread(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    context.computeIfAbsent("foo", o -> "bar");
                }
            }
            catch (ConcurrentModificationException ex) {
                exception.set(true);
            }
        }).start();
        new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                context.clear();
            }
            context.put("thread", "done");
        }).start();

        Awaitility.await().atMost(150, TimeUnit.MILLISECONDS).pollDelay(10, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(exception).as("ConcurrentModificationException must not be thrown").isFalse();
            assertThat((String) context.get("thread")).isEqualTo("done");
        });
    }

    @Test
    void observe() {
        String result;
        // with supplier
        result = Observation.start("service", registry).observe(() -> "foo");
        assertThat(result).isEqualTo("foo");

        // with function
        result = Observation.start("service", registry).observeWithContext((context) -> "bar");
        assertThat(result).isEqualTo("bar");

        // with runnable
        AtomicBoolean called = new AtomicBoolean();
        Observation.start("service", registry).observe(() -> called.set(true));
        assertThat(called).isTrue();
    }

    @Test
    void observeWithFunction() {
        CustomContext context = new CustomContext();
        AtomicReference<CustomContext> contextHolder = new AtomicReference<>();
        String result = new SimpleObservation("service", registry, context).observeWithContext((ctx) -> {
            CustomContext customContext = (CustomContext) ctx;
            customContext.hello();
            contextHolder.set(customContext);
            return "foo";
        });
        assertThat(result).isEqualTo("foo");
        assertThat(contextHolder).hasValueSatisfying((value) -> assertThat(value).isSameAs(context));

        // passing function as a method arg
        Function<CustomContext, String> function = (customContext) -> {
            customContext.hello(); // compilation check
            return "bar";
        };
        result = new SimpleObservation("service", registry, context).observeWithContext(function);
        assertThat(result).isEqualTo("bar");

        // method reference
        result = new SimpleObservation("service", registry, context).observeWithContext(this::convert);
        assertThat(result).isEqualTo("Hi");

        // with explicit type cast on method
        result = new SimpleObservation("service", registry, context)
            .<CustomContext, String>observeWithContext((ctx) -> {
                ctx.hello(); // compilation check
                return "Hello";
            });
        assertThat(result).isEqualTo("Hello");

        // with Noop registry, supplier provided context will not be created
        AtomicBoolean contextCreated = new AtomicBoolean();
        Supplier<Context> supplier = () -> new Context() {
            {
                contextCreated.set(true);
            }
        };
        AtomicReference<Context> passedContextHolder = new AtomicReference<>();
        result = Observation.createNotStarted("service", supplier, ObservationRegistry.NOOP)
            .observeWithContext((ctx) -> {
                passedContextHolder.set(ctx);
                return "World";
            });
        assertThat(passedContextHolder).as("passed a noop context").hasValue(Observation.NOOP.getContext());
        assertThat(contextCreated).isFalse();
        assertThat(result).isEqualTo("World");
    }

    private String convert(CustomContext customContext) {
        return "Hi";
    }

    static class Service {

        String executeCallable() throws IOException {
            if (new Random().nextBoolean()) {
                throw new IOException();
            }
            return "";
        }

        void executeRunnable() throws IOException {
            if (new Random().nextBoolean()) {
                throw new IOException();
            }
        }

    }

    static class CustomConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "custom.name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "custom.contextualName";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low", "custom", "custom", "present");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class DefaultConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "default.name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "default.contextualName";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low", "default", "default", "present");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class GlobalConvention implements GlobalObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "global.name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "global.contextualName";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low", "global", "global", "present");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class InstrumentationConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "instrumentation.name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "instrumentation.contextualName";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low", "instrumentation", "instrumentation", "present");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class CustomContext extends Observation.Context {

        String hello() {
            return "Hello";
        }

    }

}
