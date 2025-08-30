/**
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.observation.aop;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import io.micrometer.common.ImmutableExtendedKeyValue;
import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservedKeyValueTag;

/**
 * Support for {@link ObservedKeyValueTag}.
 *
 * @author Seungyong Hong
 */
public class ObservedKeyValueTagSupport {

    public static void addTag(KeyValue keyValue, Observation.Context context) {
        if (!(keyValue instanceof ImmutableExtendedKeyValue)) {
            throw new IllegalStateException("keyValue must be an instance of AuunotationImmutableKeyValue");
        }

        ImmutableExtendedKeyValue<ObservedKeyValueTag> auunotationImmutableKeyValue = (ImmutableExtendedKeyValue<ObservedKeyValueTag>) keyValue;
        ObservedKeyValueTag observedKeyValueTag = auunotationImmutableKeyValue.getData();
        switch (observedKeyValueTag.cardinality()) {
            case LOW:
                context.addLowCardinalityKeyValue(keyValue);
                break;
            case HIGH:
                context.addHighCardinalityKeyValue(keyValue);
                break;
        }
    }

    public static String resolveTagKey(ObservedKeyValueTag observedKeyValueTag) {
        return StringUtils.isNotBlank(observedKeyValueTag.value()) ? observedKeyValueTag.value()
                : observedKeyValueTag.key();
    }

    public static String resolveTagValue(ObservedKeyValueTag annotation, @Nullable Object argument,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        if (annotation.resolver() != NoOpValueResolver.class) {
            ValueResolver valueResolver = resolverProvider.apply(annotation.resolver());

            return valueResolver.resolve(argument);
        }
        else if (StringUtils.isNotBlank(annotation.expression())) {

            return expressionResolverProvider.apply(ValueExpressionResolver.class)
                .resolve(annotation.expression(), argument);
        }
        else if (argument != null) {

            return argument.toString();
        }

        return "";
    }

}
