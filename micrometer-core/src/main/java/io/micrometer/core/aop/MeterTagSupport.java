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

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Support for {@link MeterTag}.
 *
 * @author Marcin Grzejszczak
 * @author Johnny Lim
 * @author Seungyong Hong
 */
final class MeterTagSupport {

    private MeterTagSupport() {
    }

    static String resolveTagKey(MeterTag annotation) {
        return StringUtils.isNotBlank(annotation.value()) ? annotation.value() : annotation.key();
    }

    /**
     * Similar to {@code ObservationKeyValueSupport.resolveTagValue}. The two logics are
     * similar, so if one is modified, probably the other one should be modified too.
     */
    static String resolveTagValue(MeterTag annotation, @Nullable Object argument,
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
