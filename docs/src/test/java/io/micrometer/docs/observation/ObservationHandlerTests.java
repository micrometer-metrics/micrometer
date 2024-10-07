/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.docs.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.*;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.docs.ObservationDocumentation;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.lang.Nullable;

import static io.micrometer.docs.observation.ObservationHandlerTests.TaxObservationDocumentation.TaxHighCardinalityKeyNames.USER_ID;
import static io.micrometer.docs.observation.ObservationHandlerTests.TaxObservationDocumentation.TaxLowCardinalityKeyNames.TAX_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sources for observation-components.adoc
 */
class ObservationHandlerTests {

    @Test
    void timer() {
        // tag::timer[]
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer.Sample sample = Timer.start(registry);
        try {
            // do some work here
        }
        finally {
            sample.stop(Timer.builder("my.timer").register(registry));
        }
        // end::timer[]
    }

    @Test
    void observation_instead_of_timer() {
        // tag::observation[]
        ObservationRegistry registry = ObservationRegistry.create();
        Observation.createNotStarted("my.operation", registry).observe(this::doSomeWorkHere);
        // end::observation[]
    }

    @Test
    void registering_handler() {
        // tag::register_handler[]
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new SimpleHandler());
        // end::register_handler[]
    }

    @Test
    void instrumenting_code() {
        // tag::instrumenting_code[]
        ObservationRegistry registry = ObservationRegistry.create();
        Observation.Context context = new Observation.Context().put(String.class, "test");
        // using a context is optional, so you can call createNotStarted without it:
        // Observation.createNotStarted(name, registry)
        Observation.createNotStarted("my.operation", () -> context, registry).observe(this::doSomeWorkHere);
        // end::instrumenting_code[]
    }

    @Test
    void manual_scoping() {
        // tag::manual_scoping[]
        ObservationRegistry registry = ObservationRegistry.create();
        Observation.Context context = new Observation.Context().put(String.class, "test");
        // using a context is optional, so you can call start without it:
        // Observation.start(name, registry)
        Observation observation = Observation.start("my.operation", () -> context, registry);
        try (Observation.Scope scope = observation.openScope()) {
            doSomeWorkHere();
        }
        catch (Exception ex) {
            observation.error(ex); // and don't forget to handle exceptions
            throw ex;
        }
        finally {
            observation.stop();
        }
        // end::manual_scoping[]
    }

    @Test
    void error_and_event() {
        // tag::error_and_event[]
        ObservationRegistry registry = ObservationRegistry.create();
        Observation observation = Observation.start("my.operation", registry);
        try (Observation.Scope scope = observation.openScope()) {
            observation.event(Observation.Event.of("my.event", "look what happened"));
            doSomeWorkHere();
        }
        catch (Exception exception) {
            observation.error(exception);
            throw exception;
        }
        finally {
            observation.stop();
        }
        // end::error_and_event[]
    }

    @Test
    void observation_convention_example() {
        // tag::observation_convention_example[]
        // Registry setup
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        // add metrics
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        observationRegistry.observationConfig().observationConvention(new GlobalTaxObservationConvention());
        // This will be applied to all observations
        observationRegistry.observationConfig().observationFilter(new CloudObservationFilter());

        // In this case we're overriding the default convention by passing the custom one
        TaxCalculator taxCalculator = new TaxCalculator(observationRegistry, new CustomTaxObservationConvention());
        // run the logic you want to observe
        taxCalculator.calculateTax("INCOME_TAX", "1234567890");
        // end::observation_convention_example[]
    }

    @Test
    void annotatedCallShouldBeObserved() {
        // @formatter:off
        // tag::observed_aop[]
        // create a test registry
        TestObservationRegistry registry = TestObservationRegistry.create();
        // add a system out printing handler
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        // create a proxy around the observed service
        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        // make a call
        ObservedService service = pf.getProxy();
        service.call();

        // assert that observation has been properly created
        assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("test.call")
                .hasContextualNameEqualTo("test#call")
                .hasLowCardinalityKeyValue("abc", "123")
                .hasLowCardinalityKeyValue("test", "42")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "call").doesNotHaveError();
        // end::observed_aop[]
        // @formatter:on
    }

    private void doSomeWorkHere() {

    }

    // tag::simple_handler[]
    static class SimpleHandler implements ObservationHandler<Observation.Context> {

        @Override
        public void onStart(Observation.Context context) {
            System.out.println("START " + "data: " + context.get(String.class));
        }

        @Override
        public void onError(Observation.Context context) {
            System.out.println("ERROR " + "data: " + context.get(String.class) + ", error: " + context.getError());
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            System.out.println("EVENT " + "event: " + event + " data: " + context.get(String.class));
        }

        @Override
        public void onStop(Observation.Context context) {
            System.out.println("STOP  " + "data: " + context.get(String.class));
        }

        @Override
        public boolean supportsContext(Observation.Context handlerContext) {
            // you can decide if your handler should be invoked for this context object or
            // not
            return true;
        }

    }
    // end::simple_handler[]

    // tag::tax_example[]

    /**
     * A dedicated {@link Observation.Context} used for taxing.
     */
    class TaxContext extends Observation.Context {

        private final String taxType;

        private final String userId;

        TaxContext(String taxType, String userId) {
            this.taxType = taxType;
            this.userId = userId;
        }

        String getTaxType() {
            return taxType;
        }

        String getUserId() {
            return userId;
        }

    }

    /**
     * An example of an {@link ObservationFilter} that will add the key-values to all
     * observations.
     */
    class CloudObservationFilter implements ObservationFilter {

        @Override
        public Observation.Context map(Observation.Context context) {
            return context.addLowCardinalityKeyValue(KeyValue.of("cloud.zone", CloudUtils.getZone()))
                .addHighCardinalityKeyValue(KeyValue.of("cloud.instance.id", CloudUtils.getCloudInstanceId()));
        }

    }

    /**
     * An example of an {@link ObservationConvention} that renames the tax related
     * observations and adds cloud related tags to all contexts. When registered via the
     * `ObservationRegistry#observationConfig#observationConvention` will override the
     * default {@link TaxObservationConvention}. If the user provides a custom
     * implementation of the {@link TaxObservationConvention} and passes it to the
     * instrumentation, the custom implementation wins.
     *
     * In other words
     *
     * 1) Custom {@link ObservationConvention} has precedence 2) If no custom convention
     * was passed and there's a matching {@link GlobalObservationConvention} it will be
     * picked 3) If there's no custom, nor matching global convention, the default
     * {@link ObservationConvention} will be used
     *
     * If you need to add some key-values regardless of the used
     * {@link ObservationConvention} you should use an {@link ObservationFilter}.
     */
    class GlobalTaxObservationConvention implements GlobalObservationConvention<TaxContext> {

        // this will be applicable for all tax contexts - it will rename all the tax
        // contexts
        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TaxContext;
        }

        @Override
        public String getName() {
            return "global.tax.calculate";
        }

    }

    // Interface for an ObservationConvention related to calculating Tax
    interface TaxObservationConvention extends ObservationConvention<TaxContext> {

        @Override
        default boolean supportsContext(Observation.Context context) {
            return context instanceof TaxContext;
        }

    }

    /**
     * Default convention of tags related to calculating tax. If no user one or global
     * convention will be provided then this one will be picked.
     */
    class DefaultTaxObservationConvention implements TaxObservationConvention {

        @Override
        public KeyValues getLowCardinalityKeyValues(TaxContext context) {
            return KeyValues.of(TAX_TYPE.withValue(context.getTaxType()));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(TaxContext context) {
            return KeyValues.of(USER_ID.withValue(context.getUserId()));
        }

        @Override
        public String getName() {
            return "default.tax.name";
        }

    }

    /**
     * If micrometer-docs-generator is used, we will automatically generate documentation
     * for your observations. Check this URL
     * https://github.com/micrometer-metrics/micrometer-docs-generator#documentation for
     * setup example and read the {@link ObservationDocumentation} javadocs.
     */
    enum TaxObservationDocumentation implements ObservationDocumentation {

        CALCULATE {
            @Override
            public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return DefaultTaxObservationConvention.class;
            }

            @Override
            public String getContextualName() {
                return "calculate tax";
            }

            @Override
            public String getPrefix() {
                return "tax";
            }

            @Override
            public KeyName[] getLowCardinalityKeyNames() {
                return TaxLowCardinalityKeyNames.values();
            }

            @Override
            public KeyName[] getHighCardinalityKeyNames() {
                return TaxHighCardinalityKeyNames.values();
            }
        };

        enum TaxLowCardinalityKeyNames implements KeyName {

            TAX_TYPE {
                @Override
                public String asString() {
                    return "tax.type";
                }
            }

        }

        enum TaxHighCardinalityKeyNames implements KeyName {

            USER_ID {
                @Override
                public String asString() {
                    return "tax.user.id";
                }
            }

        }

    }

    /**
     * Our business logic that we want to observe.
     */
    class TaxCalculator {

        private final ObservationRegistry observationRegistry;

        // If the user wants to override the default they can override this. Otherwise,
        // it will be {@code null}.
        @Nullable
        private final TaxObservationConvention observationConvention;

        TaxCalculator(ObservationRegistry observationRegistry,
                @Nullable TaxObservationConvention observationConvention) {
            this.observationRegistry = observationRegistry;
            this.observationConvention = observationConvention;
        }

        void calculateTax(String taxType, String userId) {
            // Create a new context
            TaxContext taxContext = new TaxContext(taxType, userId);
            // Create a new observation
            TaxObservationDocumentation.CALCULATE
                .observation(this.observationConvention, new DefaultTaxObservationConvention(), () -> taxContext,
                        this.observationRegistry)
                // Run the actual logic you want to observe
                .observe(this::calculateInterest);
        }

        private void calculateInterest() {
            // do some work
        }

    }

    /**
     * Example of user changing the default conventions.
     */
    class CustomTaxObservationConvention extends DefaultTaxObservationConvention {

        @Override
        public KeyValues getLowCardinalityKeyValues(TaxContext context) {
            return super.getLowCardinalityKeyValues(context)
                .and(KeyValue.of("additional.low.cardinality.tag", "value"));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(TaxContext context) {
            return KeyValues.of("this.would.override.the.default.high.cardinality.tags", "value");
        }

        @Override
        public String getName() {
            return "tax.calculate";
        }

    }

    /**
     * A utility class to set cloud related arguments.
     */
    static class CloudUtils {

        static String getZone() {
            return "...";
        }

        static String getCloudInstanceId() {
            return "...";
        }

    }
    // end::tax_example[]

    // tag::observed_service[]
    static class ObservedService {

        @Observed(name = "test.call", contextualName = "test#call",
                lowCardinalityKeyValues = { "abc", "123", "test", "42" })
        void call() {
            System.out.println("call");
        }

    }
    // end::observed_service[]

}
