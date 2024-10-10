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
package io.micrometer.core.aop;

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.core.annotation.Timed;
import io.micrometer.samples.spring6.aop.SpelValueExpressionResolver;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MeterTagSupport}.
 *
 * @author Marcin Grzejszczak
 * @author Johnny Lim
 */
class MeterTagSupportTests {

    ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver";

    ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

    @Test
    void shouldUseCustomTagValueResolver() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueResolver", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        assertThat(annotation).isInstanceOf(MeterTag.class);
        String resolvedValue = MeterTagSupport.resolveTagValue((MeterTag) annotation, "test", aClass -> valueResolver,
                aClass -> valueExpressionResolver);
        assertThat(resolvedValue).isEqualTo("Value from myCustomTagValueResolver");
    }

    @Test
    void shouldUseTagValueExpression() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueExpression", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        assertThat(annotation).isInstanceOf(MeterTag.class);
        String resolvedValue = MeterTagSupport.resolveTagValue((MeterTag) annotation, "test value",
                aClass -> valueResolver, aClass -> valueExpressionResolver);
        assertThat(resolvedValue).isEqualTo("hello test value characters");
    }

    @Test
    void shouldReturnArgumentToString() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForArgumentToString", Long.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        assertThat(annotation).isInstanceOf(MeterTag.class);
        String resolvedValue = MeterTagSupport.resolveTagValue((MeterTag) annotation, 15, aClass -> valueResolver,
                aClass -> valueExpressionResolver);
        assertThat(resolvedValue).isEqualTo("15");
    }

    static class AnnotationMockClass {

        @Timed
        public void getAnnotationForTagValueResolver(
                @MeterTag(key = "test", resolver = ValueResolver.class) String test) {
        }

        @Timed
        public void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello ' + #this + ' characters'") String test) {
        }

        @Timed
        public void getAnnotationForArgumentToString(@MeterTag("test") Long param) {
        }

    }

}
