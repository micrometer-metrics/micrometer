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
import io.micrometer.common.annotation.NoOpTagValueResolver;
import io.micrometer.common.annotation.TagAnnotationHandler;
import io.micrometer.common.annotation.TagValueExpressionResolver;
import io.micrometer.common.annotation.TagValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Timer;

import java.util.function.Function;

/**
 * Annotation handler for {@link MetricTag}.
 *
 * @since 1.11.0
 */
public class MetricsTagAnnotationHandler extends TagAnnotationHandler<Timer.Builder> {

    /**
     * Creates a new instance of {@link MetricsTagAnnotationHandler}.
     * @param resolverProvider function to retrieve a {@link TagValueResolver}
     * @param expressionResolverProvider function to retrieve a
     * {@link TagValueExpressionResolver}
     */
    public MetricsTagAnnotationHandler(
            Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider,
            Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider) {
        super((keyValue, builder) -> builder.tag(keyValue.getKey(), keyValue.getValue()), resolverProvider,
                expressionResolverProvider, MetricTag.class, (annotation, o) -> {
                    if (!(annotation instanceof MetricTag)) {
                        return null;
                    }
                    MetricTag metricTag = (MetricTag) annotation;
                    return KeyValue.of(resolveTagKey(metricTag),
                            resolveTagValue(metricTag, o, resolverProvider, expressionResolverProvider));
                });
    }

    private static String resolveTagKey(MetricTag annotation) {
        return StringUtils.isNotBlank(annotation.value()) ? annotation.value() : annotation.key();
    }

    static String resolveTagValue(MetricTag annotation, Object argument,
            Function<Class<? extends TagValueResolver>, ? extends TagValueResolver> resolverProvider,
            Function<Class<? extends TagValueExpressionResolver>, ? extends TagValueExpressionResolver> expressionResolverProvider) {
        String value = null;
        if (annotation.resolver() != NoOpTagValueResolver.class) {
            TagValueResolver tagValueResolver = resolverProvider.apply(annotation.resolver());
            value = tagValueResolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {
            value = expressionResolverProvider.apply(TagValueExpressionResolver.class)
                .resolve(annotation.expression(), argument);
        }
        else if (argument != null) {
            value = argument.toString();
        }
        return value == null ? "" : value;
    }

}
