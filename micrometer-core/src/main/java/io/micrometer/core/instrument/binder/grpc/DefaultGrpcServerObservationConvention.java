/*
 * Copyright 2022 the original author or authors.
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
package io.micrometer.core.instrument.binder.grpc;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.LowCardinalityKeyNames;

import java.util.ArrayList;
import java.util.List;

/**
 * Default convention for gRPC server. This class defines how to extract values from
 * {@link GrpcServerObservationContext}.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public class DefaultGrpcServerObservationConvention implements GrpcServerObservationConvention {

    @Override
    public String getName() {
        return "grpc.server";
    }

    @Override
    public String getContextualName(GrpcServerObservationContext context) {
        return context.getFullMethodName();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(GrpcServerObservationContext context) {
        List<KeyValue> keyValues = new ArrayList<>();
        keyValues.add(LowCardinalityKeyNames.METHOD.withValue(context.getMethodName()));
        keyValues.add(LowCardinalityKeyNames.SERVICE.withValue(context.getServiceName()));
        keyValues.add(LowCardinalityKeyNames.METHOD_TYPE.withValue(context.getMethodType().name()));
        if (context.getStatusCode() != null) {
            keyValues.add(LowCardinalityKeyNames.STATUS_CODE.withValue(context.getStatusCode().name()));
        }
        keyValues.add(LowCardinalityKeyNames.CANCELLED.withValue(Boolean.toString(context.isCancelled())));
        return KeyValues.of(keyValues);
    }

}
