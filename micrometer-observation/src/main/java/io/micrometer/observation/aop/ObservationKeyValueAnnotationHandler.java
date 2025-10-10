/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.observation.aop;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.GenericAnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservationKeyValue;

import java.lang.annotation.Annotation;
import java.util.function.Function;

import static io.micrometer.observation.aop.ObservationKeyValueSupport.resolveKey;
import static io.micrometer.observation.aop.ObservationKeyValueSupport.resolveValue;

/**
 * Annotation handler for {@link ObservationKeyValue}. To add support for
 * {@link ObservationKeyValue} on {@link ObservedAspect} check the
 * {@link ObservedAspect#setObservationKeyValueAnnotationHandler(ObservationKeyValueAnnotationHandler)}
 * method.
 *
 * @author Seungyong Hong
 * @author Jonatan Ivanov
 */
public class ObservationKeyValueAnnotationHandler extends
        GenericAnnotationHandler<ObservationKeyValueAnnotationHandler.KeyValueWithCardinality, Observation.Context> {

    public ObservationKeyValueAnnotationHandler(
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        super(ObservationKeyValueAnnotationHandler::addKeyValue, resolverProvider, expressionResolverProvider,
                ObservationKeyValue.class,
                (annotation, object) -> toKeyValue(annotation, object, resolverProvider, expressionResolverProvider));
    }

    private static void addKeyValue(KeyValueWithCardinality keyValueWithCardinality, Observation.Context context) {
        if (keyValueWithCardinality.cardinality == Cardinality.LOW) {
            context.addLowCardinalityKeyValue(keyValueWithCardinality.getDelegate());
        }
        else {
            context.addHighCardinalityKeyValue(keyValueWithCardinality.getDelegate());
        }
    }

    private static KeyValueWithCardinality toKeyValue(Annotation annotation, Object object,
            Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
            Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider) {
        ObservationKeyValue observationKeyValue = (ObservationKeyValue) annotation;
        KeyValue keyValue = KeyValue.of(resolveKey(observationKeyValue),
                resolveValue(observationKeyValue, object, resolverProvider, expressionResolverProvider));
        return new KeyValueWithCardinality(keyValue, observationKeyValue.cardinality());
    }

    static class KeyValueWithCardinality implements KeyValue {

        private final KeyValue keyValue;

        private final Cardinality cardinality;

        private KeyValueWithCardinality(KeyValue keyValue, Cardinality cardinality) {
            this.keyValue = keyValue;
            this.cardinality = cardinality;
        }

        @Override
        public String getKey() {
            return this.keyValue.getKey();
        }

        @Override
        public String getValue() {
            return this.keyValue.getValue();
        }

        private KeyValue getDelegate() {
            return this.keyValue;
        }

    }

}
