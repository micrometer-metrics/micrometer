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
package io.micrometer.core.aop;

import io.micrometer.common.annotation.TagValueExpressionResolver;
import io.micrometer.common.annotation.TagValueResolver;
import io.micrometer.core.annotation.Timed;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class NullMetricTagAnnotationHandlerTests {

    TagValueResolver tagValueResolver = parameter -> null;

    TagValueExpressionResolver tagValueExpressionResolver = (expression, parameter) -> "";

    MetricsTagAnnotationHandler handler = new MetricsTagAnnotationHandler(aClass -> tagValueResolver,
            aClass -> tagValueExpressionResolver);

    @Test
    void shouldUseEmptyStringWheCustomTagValueResolverReturnsNull() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueResolver", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof MetricTag) {
            String resolvedValue = this.handler.resolveTagValue((MetricTag) annotation, "test",
                    aClass -> tagValueResolver, aClass -> tagValueExpressionResolver);
            assertThat(resolvedValue).isEqualTo("");
        }
        else {
            fail("Annotation was not MetricTag");
        }
    }

    @Test
    void shouldUseEmptyStringWhenTagValueExpressionReturnNull() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForTagValueExpression", String.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof MetricTag) {
            String resolvedValue = this.handler.resolveTagValue((MetricTag) annotation, "test",
                    aClass -> tagValueResolver, aClass -> tagValueExpressionResolver);

            assertThat(resolvedValue).isEqualTo("");
        }
        else {
            fail("Annotation was not MetricTag");
        }
    }

    @Test
    void shouldUseEmptyStringWhenArgumentIsNull() throws NoSuchMethodException, SecurityException {
        Method method = AnnotationMockClass.class.getMethod("getAnnotationForArgumentToString", Long.class);
        Annotation annotation = method.getParameterAnnotations()[0][0];
        if (annotation instanceof MetricTag) {
            String resolvedValue = this.handler.resolveTagValue((MetricTag) annotation, null,
                    aClass -> tagValueResolver, aClass -> tagValueExpressionResolver);
            assertThat(resolvedValue).isEqualTo("");
        }
        else {
            fail("Annotation was not SpanTag");
        }
    }

    protected class AnnotationMockClass {

        @Timed
        public void getAnnotationForTagValueResolver(
                @MetricTag(key = "test", resolver = TagValueResolver.class) String test) {
        }

        @Timed
        public void getAnnotationForTagValueExpression(@MetricTag(key = "test", expression = "null") String test) {
        }

        @Timed
        public void getAnnotationForArgumentToString(@MetricTag("test") Long param) {
        }

    }

}
