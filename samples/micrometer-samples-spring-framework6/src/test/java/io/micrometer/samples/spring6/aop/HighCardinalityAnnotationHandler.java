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
package io.micrometer.samples.spring6.aop;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;

import io.micrometer.observation.Observation;

import java.util.function.Function;

/**
 * Taken from Tracing for testing.
 */
class HighCardinalityAnnotationHandler extends AnnotationHandler<Observation> {

    public HighCardinalityAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        super((keyValue, observation) -> observation.highCardinalityKeyValue(keyValue), resolverProvider,
                expressionResolverProvider, HighCardinality.class, (annotation, o) -> {
                    if (!(annotation instanceof HighCardinality)) {
                        return null;
                    }
                    HighCardinality highCardinality = (HighCardinality) annotation;
                    return KeyValue.of(resolveTagKey(highCardinality),
                            resolveTagValue(highCardinality, o, resolverProvider, expressionResolverProvider));
                });
    }

    private static String resolveTagKey(HighCardinality annotation) {
        return StringUtils.isNotBlank(annotation.value()) ? annotation.value() : annotation.key();
    }

    static String resolveTagValue(HighCardinality annotation, Object argument,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        String value = null;
        if (annotation.resolver() != NoOpValueResolver.class) {
            ValueResolver ValueResolver = resolverProvider.apply(annotation.resolver());
            value = ValueResolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {
            value = expressionResolverProvider.apply(ValueExpressionResolver.class)
                .resolve(annotation.expression(), argument);
        }
        else if (argument != null) {
            value = argument.toString();
        }
        return value == null ? "" : value;
    }

}
