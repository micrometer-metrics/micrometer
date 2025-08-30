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

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservedKeyValueTag;

import java.util.function.Function;

/**
 * Annotation handler for {@link ObservedKeyValueTag}. To add support for
 * {@link ObservedKeyValueTag} on {@link ObservedAspect} check the
 * {@link ObservedAspect#setObservedKeyValueTagAnnotationHandler(ObservedKeyValueTagAnnotationHandler)}
 * method.
 *
 * @author Seungyong Hong
 */
public class ObservedKeyValueTagAnnotationHandler extends AnnotationHandler<Observation.Context> {

    /**
     * Creates a new instance of {@link ObservedKeyValueTagAnnotationHandler}.
     * @param resolverProvider function to retrieve a {@link ValueResolver}
     * @param expressionResolverProvider function to retrieve a
     * {@link ValueExpressionResolver}
     */
    public ObservedKeyValueTagAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        super(ObservedKeyValueTagSupport::addTag, resolverProvider, expressionResolverProvider,
                ObservedKeyValueTag.class, (annotation, object) -> {
                    if (!(annotation instanceof ObservedKeyValueTag)) {
                        return null;
                    }

                    ObservedKeyValueTag observedKeyValueTag = (ObservedKeyValueTag) annotation;
                    return KeyValue.of(ObservedKeyValueTagSupport.resolveTagKey(observedKeyValueTag),
                            ObservedKeyValueTagSupport.resolveTagValue(observedKeyValueTag, object, resolverProvider,
                                    expressionResolverProvider),
                            annotation);
                });
    }

}
