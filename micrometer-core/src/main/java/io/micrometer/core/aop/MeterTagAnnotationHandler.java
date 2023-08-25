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

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Timer;

import java.util.function.Function;

/**
 * Annotation handler for {@link MeterTag}. To add support for {@link MeterTag} on
 * {@link TimedAspect} check the
 * {@link TimedAspect#setMeterTagAnnotationHandler(MeterTagAnnotationHandler)} method.
 *
 * @since 1.11.0
 */
public class MeterTagAnnotationHandler extends AnnotationHandler<Timer.Builder> {

    /**
     * Creates a new instance of {@link MeterTagAnnotationHandler}.
     * @param resolverProvider function to retrieve a {@link ValueResolver}
     * @param expressionResolverProvider function to retrieve a
     * {@link ValueExpressionResolver}
     */
    public MeterTagAnnotationHandler(Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        super((keyValue, builder) -> builder.tag(keyValue.getKey(), keyValue.getValue()), resolverProvider,
                expressionResolverProvider, MeterTag.class, (annotation, o) -> {
                    if (!(annotation instanceof MeterTag)) {
                        return null;
                    }
                    MeterTag meterTag = (MeterTag) annotation;
                    return KeyValue.of(resolveTagKey(meterTag),
                            resolveTagValue(meterTag, o, resolverProvider, expressionResolverProvider));
                });
    }

    private static String resolveTagKey(MeterTag annotation) {
        return StringUtils.isNotBlank(annotation.value()) ? annotation.value() : annotation.key();
    }

    static String resolveTagValue(MeterTag annotation, Object argument,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        String value = null;
        if (annotation.resolver() != NoOpValueResolver.class) {
            ValueResolver valueResolver = resolverProvider.apply(annotation.resolver());
            value = valueResolver.resolve(argument);
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
