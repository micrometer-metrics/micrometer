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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.engine.execution.BeforeEachMethodAdapter;
import org.junit.jupiter.engine.extension.ExtensionRegistry;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.*;

abstract class InstrumentationVerificationTests {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private final TestObservationRegistry testObservationRegistry = createObservationRegistryWithMetrics();

    /**
     * {@link MeterRegistry} to use for instrumentation verification tests.
     * @return registry to use
     */
    protected MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Helper method for creating a {@link TestObservationRegistry} to run the tests also
     * using the Observation API.
     * @return a {@link TestObservationRegistry} with a
     * {@link DefaultMeterObservationHandler} registered
     */
    protected TestObservationRegistry createObservationRegistryWithMetrics() {
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(getRegistry()));
        return observationRegistry;
    }

    protected TestObservationRegistry getObservationRegistry() {
        return this.testObservationRegistry;
    }

    /**
     * Enum that represents types of tests we can run.
     */
    enum TestType {

        /**
         * Runs the tests by just using the {@link MeterRegistry}.
         */
        METRICS_VIA_METER_REGISTRY,

        /**
         * Runs the tests by using the Observation API and a MeterObservationHandler.
         */
        METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER

    }

    // Allows parameter resolution in BeforeEach and AfterEach
    // Based on
    // https://code-case.hashnode.dev/how-to-pass-parameterized-test-parameters-to-beforeeachaftereach-method-in-junit5
    static class AfterBeforeParameterResolver implements BeforeEachMethodAdapter, ParameterResolver {

        private ParameterResolver parameterisedTestParameterResolver = null;

        @Override
        public void invokeBeforeEachMethod(ExtensionContext context, ExtensionRegistry registry) {
            Optional<ParameterResolver> resolverOptional = registry.getExtensions(ParameterResolver.class)
                .stream()
                .filter(parameterResolver -> parameterResolver.getClass()
                    .getName()
                    .contains("ParameterizedTestParameterResolver"))
                .findFirst();
            if (!resolverOptional.isPresent()) {
                throw new IllegalStateException(
                        "ParameterizedTestParameterResolver missed in the registry. Probably it's not a Parameterized Test");
            }
            else {
                parameterisedTestParameterResolver = resolverOptional.get();
            }
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                throws ParameterResolutionException {
            if (isExecutedOnAfterOrBeforeMethod(parameterContext)) {
                ParameterContext pContext = getMappedContext(parameterContext, extensionContext);
                return parameterisedTestParameterResolver.supportsParameter(pContext, extensionContext);
            }
            return false;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return parameterisedTestParameterResolver
                .resolveParameter(getMappedContext(parameterContext, extensionContext), extensionContext);
        }

        private MappedParameterContext getMappedContext(ParameterContext parameterContext,
                ExtensionContext extensionContext) {
            return new MappedParameterContext(parameterContext.getIndex(),
                    extensionContext.getRequiredTestMethod().getParameters()[parameterContext.getIndex()],
                    Optional.of(parameterContext.getTarget()));
        }

        private boolean isExecutedOnAfterOrBeforeMethod(ParameterContext parameterContext) {
            return Arrays.stream(parameterContext.getDeclaringExecutable().getDeclaredAnnotations())
                .anyMatch(this::isAfterEachOrBeforeEachAnnotation);
        }

        private boolean isAfterEachOrBeforeEachAnnotation(Annotation annotation) {
            return annotation.annotationType() == BeforeEach.class || annotation.annotationType() == AfterEach.class;
        }

    }

    // Based on
    // https://code-case.hashnode.dev/how-to-pass-parameterized-test-parameters-to-beforeeachaftereach-method-in-junit5
    static class MappedParameterContext implements ParameterContext {

        private final int index;

        private final Parameter parameter;

        private final Optional<Object> target;

        MappedParameterContext(int index, Parameter parameter, Optional<Object> target) {
            this.index = index;
            this.parameter = parameter;
            this.target = target;
        }

        @Override
        public boolean isAnnotated(Class<? extends Annotation> annotationType) {
            return AnnotationUtils.isAnnotated(parameter, annotationType);
        }

        @Override
        public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationType) {
            return AnnotationUtils.findAnnotation(parameter, annotationType);
        }

        @Override
        public <A extends Annotation> List<A> findRepeatableAnnotations(Class<A> annotationType) {
            return AnnotationUtils.findRepeatableAnnotations(parameter, annotationType);
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public Parameter getParameter() {
            return parameter;
        }

        @Override
        public Optional<Object> getTarget() {
            return target;
        }

    }

}
