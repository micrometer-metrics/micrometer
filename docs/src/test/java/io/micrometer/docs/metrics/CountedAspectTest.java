/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.docs.metrics;

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.CountedMeterTagAnnotationHandler;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

class CountedAspectTest {

    // tag::resolvers[]
    ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

    // Example of a ValueExpressionResolver that uses Spring Expression Language
    ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

    // end::resolvers[]

    @ParameterizedTest
    @EnumSource(AnnotatedTestClass.class)
    void meterTagsWithText(AnnotatedTestClass annotatedClass) {
        MeterRegistry registry = new SimpleMeterRegistry();
        CountedAspect countedAspect = new CountedAspect(registry);
        // tag::meter_tag_annotation_handler[]
        // Setting the handler on the aspect
        countedAspect.setMeterTagAnnotationHandler(
                new CountedMeterTagAnnotationHandler(aClass -> valueResolver, aClass -> valueExpressionResolver));
        // end::meter_tag_annotation_handler[]

        AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
        pf.addAspect(countedAspect);

        MeterTagClassInterface service = pf.getProxy();

        // tag::example_value_to_string[]
        service.getAnnotationForArgumentToString(15L);

        assertThat(registry.get("method.counted").tag("test", "15").counter().count()).isEqualTo(1);
        // end::example_value_to_string[]
    }

    @ParameterizedTest
    @EnumSource(AnnotatedTestClass.class)
    void meterTagsWithResolver(AnnotatedTestClass annotatedClass) {
        MeterRegistry registry = new SimpleMeterRegistry();
        CountedAspect countedAspect = new CountedAspect(registry);
        countedAspect.setMeterTagAnnotationHandler(
                new CountedMeterTagAnnotationHandler(aClass -> valueResolver, aClass -> valueExpressionResolver));

        AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
        pf.addAspect(countedAspect);

        MeterTagClassInterface service = pf.getProxy();

        // @formatter:off
        // tag::example_value_resolver[]
        service.getAnnotationForTagValueResolver("foo");

        assertThat(registry.get("method.counted")
            .tag("test", "Value from myCustomTagValueResolver [foo]")
            .counter()
            .count()).isEqualTo(1);
        // end::example_value_resolver[]
        // @formatter:on
    }

    @ParameterizedTest
    @EnumSource(AnnotatedTestClass.class)
    void meterTagsWithExpression(AnnotatedTestClass annotatedClass) {
        MeterRegistry registry = new SimpleMeterRegistry();
        CountedAspect countedAspect = new CountedAspect(registry);
        countedAspect.setMeterTagAnnotationHandler(
                new CountedMeterTagAnnotationHandler(aClass -> valueResolver, aClass -> valueExpressionResolver));

        AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
        pf.addAspect(countedAspect);

        MeterTagClassInterface service = pf.getProxy();

        // tag::example_value_spel[]
        service.getAnnotationForTagValueExpression("15L");

        assertThat(registry.get("method.counted").tag("test", "hello characters").counter().count()).isEqualTo(1);
        // end::example_value_spel[]
    }

    @ParameterizedTest
    @EnumSource(AnnotatedTestClass.class)
    void multipleMeterTagsWithExpression(AnnotatedTestClass annotatedClass) {
        MeterRegistry registry = new SimpleMeterRegistry();
        CountedAspect countedAspect = new CountedAspect(registry);
        countedAspect.setMeterTagAnnotationHandler(
                new CountedMeterTagAnnotationHandler(aClass -> valueResolver, aClass -> valueExpressionResolver));

        AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
        pf.addAspect(countedAspect);

        MeterTagClassInterface service = pf.getProxy();

        // tag::example_multi_annotations[]
        service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

        assertThat(registry.get("method.counted")
            .tag("value1", "value1: zxe")
            .tag("value2", "value2: qwe")
            .counter()
            .count()).isEqualTo(1);
        // end::example_multi_annotations[]
    }

    enum AnnotatedTestClass {

        CLASS_WITHOUT_INTERFACE(MeterTagClass.class), CLASS_WITH_INTERFACE(MeterTagClassChild.class);

        private final Class<? extends MeterTagClassInterface> clazz;

        AnnotatedTestClass(Class<? extends MeterTagClassInterface> clazz) {
            this.clazz = clazz;
        }

        @SuppressWarnings("unchecked")
        <T extends MeterTagClassInterface> T newInstance() {
            try {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    // tag::interface[]
    interface MeterTagClassInterface {

        @Counted
        void getAnnotationForTagValueResolver(@MeterTag(key = "test", resolver = ValueResolver.class) String test);

        @Counted
        void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello' + ' characters'") String test);

        @Counted
        void getAnnotationForArgumentToString(@MeterTag("test") Long param);

        @Counted
        void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                        expression = "'value2: ' + value2") DataHolder param);

    }
    // end::interface[]

    static class MeterTagClass implements MeterTagClassInterface {

        @Counted
        @Override
        public void getAnnotationForTagValueResolver(
                @MeterTag(key = "test", resolver = ValueResolver.class) String test) {
        }

        @Counted
        @Override
        public void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello' + ' characters'") String test) {
        }

        @Counted
        @Override
        public void getAnnotationForArgumentToString(@MeterTag("test") Long param) {
        }

        @Counted
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                        expression = "'value2: ' + value2") DataHolder param) {
        }

    }

    static class MeterTagClassChild implements MeterTagClassInterface {

        @Counted
        @Override
        public void getAnnotationForTagValueResolver(String test) {
        }

        @Counted
        @Override
        public void getAnnotationForTagValueExpression(String test) {
        }

        @Counted
        @Override
        public void getAnnotationForArgumentToString(Long param) {
        }

        @Counted
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value2", expression = "'value2: ' + value2") DataHolder param) {
        }

    }

    static class DataHolder {

        private final String value1;

        private final String value2;

        private DataHolder(String value1, String value2) {
            this.value1 = value1;
            this.value2 = value2;
        }

        public String getValue1() {
            return value1;
        }

        public String getValue2() {
            return value2;
        }

    }

}
