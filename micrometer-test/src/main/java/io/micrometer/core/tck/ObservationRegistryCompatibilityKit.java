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
package io.micrometer.core.tck;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.instrument.observation.ObservationHandler;
import io.micrometer.api.instrument.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Base class for {@link ObservationRegistry} compatibility tests.
 * To run a {@link ObservationRegistry} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Jonatan Ivanov
 * @author Marcin Grzejszczak
 */
public abstract class ObservationRegistryCompatibilityKit {

    private ObservationRegistry registry;

    public abstract ObservationRegistry registry();
    public abstract Duration step();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
        registry = registry();
    }

    @Test
    @DisplayName("observe using handlers")
    void observeWithHandlers() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handlerThatHandlesNothing = mock(ObservationHandler.class);
        registry.observationConfig().observationHandler(handler);
        registry.observationConfig().observationHandler(handlerThatHandlesNothing);
        when(handler.supportsContext(any())).thenReturn(true);
        when(handlerThatHandlesNothing.supportsContext(any())).thenReturn(false);

        Observation observation = Observation.start("myObservation", registry);
        verify(handler).supportsContext(isA(Observation.Context.class));
        verify(handler).onStart(isA(Observation.Context.class));
        verify(handlerThatHandlesNothing).supportsContext(isA(Observation.Context.class));
        verifyNoMoreInteractions(handlerThatHandlesNothing);

        try (Observation.Scope scope = observation.openScope()) {
            verify(handler).onScopeOpened(isA(Observation.Context.class));
            assertThat(scope.getCurrentObservation()).isSameAs(observation);

            Throwable exception = new IOException("simulated");
            observation.error(exception);
            verify(handler).onError(isA(Observation.Context.class));
        }
        verify(handler).onScopeClosed(isA(Observation.Context.class));
        observation.stop();
    }

    @Test
    void runnableShouldBeScoped() {
        Observation observation = Observation.start("myObservation", registry);
        observation.scoped((Runnable) () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
        });
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void errorShouldBeReportedOnFailingScopedRunnable() {
        Observation.Context context = new Observation.Context();
        Observation observation = Observation.start("myObservation", context, registry);
        RuntimeException error = new RuntimeException("simulated");

        assertThatThrownBy(() -> observation.scoped((Runnable) () -> { throw error; })).isSameAs(error);
        assertThat(context.getError()).containsSame(error);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldBeScoped() {
        Observation observation = Observation.start("myObservation", registry);
        String result = observation.scoped((Supplier<String>) () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        });
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void errorShouldBeReportedOnFailingScopedSupplier() {
        Observation.Context context = new Observation.Context();
        Observation observation = Observation.start("myObservation", context, registry);
        RuntimeException error = new RuntimeException("simulated");

        assertThatThrownBy(() -> observation.scoped((Supplier<String>) () -> { throw error; })).isSameAs(error);
        assertThat(context.getError()).containsSame(error);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void runnableShouldBeParentScoped() {
        Observation parent = Observation.start("myObservation", registry);
        Observation.tryScoped(
                parent,
                (Runnable) () -> {
                    assertThat(registry.getCurrentObservation()).isSameAs(parent);
                });
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void runnableShouldNotBeParentScopedIfParentIsNull() {
        Observation.tryScoped(
                null,
                (Runnable) () -> {
                    assertThat(registry.getCurrentObservation()).isNull();
                });
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldBeParentScoped() {
        Observation parent = Observation.start("myObservation", registry);
        String result = Observation.tryScoped(
                parent,
                (Supplier<String>) () -> {
                    assertThat(registry.getCurrentObservation()).isSameAs(parent);
                    return "test";
                });
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldNotBeParentScopedIfParentIsNull() {
        String result = Observation.tryScoped(
                null,
                (Supplier<String>) () -> {
                    assertThat(registry.getCurrentObservation()).isNull();
                    return "test";
                });
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void observationFieldsShouldBeSetOnContext() {
        AssertingHandler assertingHandler = new AssertingHandler();
        registry.observationConfig()
                .tagsProvider(new TestTagsProvider("global"))
                .tagsProvider(new UnsupportedTagsProvider("global"))
                .observationHandler(assertingHandler);

        TestContext testContext = new TestContext();
        testContext.put("context.field", "42");
        Exception exception = new IOException("simulated");
        Observation observation = Observation.start("test.observation", testContext, registry)
                .lowCardinalityTag("lcTag1", "1")
                .lowCardinalityTag(Tag.of("lcTag2", "2"))
                .highCardinalityTag("hcTag1", "3")
                .highCardinalityTag(Tag.of("hcTag2", "4"))
                .tagsProvider(new TestTagsProvider("local"))
                .tagsProvider(new UnsupportedTagsProvider("local"))
                .contextualName("test.observation.42")
                .error(exception);
        observation.stop();

        assertingHandler.checkAssertions(context -> {
            assertThat(context).isSameAs(testContext);
            assertThat(context.getName()).isEqualTo("test.observation");
            assertThat(context.getLowCardinalityTags()).containsExactlyInAnyOrder(
                    Tag.of("lcTag1", "1"),
                    Tag.of("lcTag2", "2"),
                    Tag.of("local.context.class", "TestContext"),
                    Tag.of("global.context.class", "TestContext")
            );
            assertThat(context.getHighCardinalityTags()).containsExactlyInAnyOrder(
                    Tag.of("hcTag1", "3"),
                    Tag.of("hcTag2", "4"),
                    Tag.of("local.uuid", testContext.uuid),
                    Tag.of("global.uuid", testContext.uuid)
            );

            assertThat(context.getAllTags()).containsExactlyInAnyOrder(
                    Tag.of("lcTag1", "1"),
                    Tag.of("lcTag2", "2"),
                    Tag.of("local.context.class", "TestContext"),
                    Tag.of("global.context.class", "TestContext"),
                    Tag.of("hcTag1", "3"),
                    Tag.of("hcTag2", "4"),
                    Tag.of("local.uuid", testContext.uuid),
                    Tag.of("global.uuid", testContext.uuid)
            );

            assertThat((String) context.get("context.field")).isEqualTo("42");

            assertThat(context.getContextualName()).isEqualTo("test.observation.42");
            assertThat(context.getError()).containsSame(exception);

            assertThat(context.toString())
                    .containsOnlyOnce("name='test.observation'")
                    .containsOnlyOnce("contextualName='test.observation.42'")
                    .containsOnlyOnce("error='java.io.IOException: simulated'")
                    .containsOnlyOnce("lowCardinalityTags=[lcTag1='1', lcTag2='2', global.context.class='TestContext', local.context.class='TestContext']")
                    .containsOnlyOnce("highCardinalityTags=[hcTag1='3', hcTag2='4', global.uuid='" + testContext.uuid + "', local.uuid='" + testContext.uuid + "']")
                    .containsOnlyOnce("map=[context.field='42']");
        });
    }

    static class TestContext extends Observation.Context {
        final String uuid = UUID.randomUUID().toString();
    }

    static class TestTagsProvider implements Observation.TagsProvider<TestContext> {
        private final String id;

        public TestTagsProvider(String id) {
            this.id = id;
        }

        @Override
        public Tags getLowCardinalityTags(TestContext context) {
            return Tags.of(this.id + "." + "context.class", TestContext.class.getSimpleName());
        }

        @Override
        public Tags getHighCardinalityTags(TestContext context) {
            return Tags.of(this.id + "." + "uuid", context.uuid);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TestContext;
        }

        public String getId() {
            return this.id;
        }
    }

    static class UnsupportedTagsProvider implements Observation.TagsProvider<Observation.Context> {
        private final String id;

        public UnsupportedTagsProvider(String id) {
            this.id = id;
        }

        @Override
        public Tags getLowCardinalityTags(Observation.Context context) {
            return Tags.of(this.id + "." + "unsupported.lc", "unsupported");
        }

        @Override
        public Tags getHighCardinalityTags(Observation.Context context) {
            return Tags.of(this.id + "." + "unsupported.hc", "unsupported");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }
    }

    static class AssertingHandler implements ObservationHandler<Observation.Context> {
        private boolean stopped = false;
        private Observation.Context context = null;

        @Override
        public void onStop(Observation.Context context) {
            this.stopped = true;
            this.context = context;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        public void checkAssertions(Consumer<Observation.Context> checker) {
            assertThat(this.stopped).isTrue();
            assertThat(this.context).isNotNull();
            checker.accept(this.context);
        }
    }
}

