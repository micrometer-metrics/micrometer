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
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.annotation.ObservedKeyValue;

/**
 * Support for {@link ObservedKeyValue}.
 *
 * @author Seungyong Hong
 */
class ObservedKeyValueSupport {

    public static String resolveTagKey(ObservedKeyValue observedKeyValue) {
        return StringUtils.isNotBlank(observedKeyValue.value()) ? observedKeyValue.value() : observedKeyValue.key();
    }

    public static String resolveTagValue(ObservedKeyValue annotation, @Nullable Object argument,
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
