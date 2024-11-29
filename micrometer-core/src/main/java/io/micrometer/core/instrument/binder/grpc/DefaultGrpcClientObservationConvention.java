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

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.LowCardinalityKeyNames;

/**
 * Default convention for gRPC client. This class defines how to extract values from
 * {@link GrpcClientObservationContext}.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public class DefaultGrpcClientObservationConvention implements GrpcClientObservationConvention {

    @Override
    public String getName() {
        return "grpc.client";
    }

    @Override
    public String getContextualName(GrpcClientObservationContext context) {
        return context.getFullMethodName();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(GrpcClientObservationContext context) {
        String statusCode = context.getStatusCode() != null ? context.getStatusCode().name() : UNKNOWN;
        return KeyValues.of(LowCardinalityKeyNames.STATUS_CODE.withValue(statusCode),
                LowCardinalityKeyNames.METHOD.withValue(context.getMethodName()),
                LowCardinalityKeyNames.SERVICE.withValue(context.getServiceName()),
                LowCardinalityKeyNames.METHOD_TYPE.withValue(context.getMethodType().name()));
    }

}
