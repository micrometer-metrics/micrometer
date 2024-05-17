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

import io.grpc.Metadata;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status.Code;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.Propagator.Getter;
import io.micrometer.observation.transport.RequestReplyReceiverContext;

/**
 * {@link Observation.Context} for gRPC server.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public class GrpcServerObservationContext extends RequestReplyReceiverContext<Metadata, Object> {

    private GrpcObservationContextInfo grpcInfo;

    public GrpcServerObservationContext(Getter<Metadata> getter) {
        super(getter);
        this.grpcInfo = new GrpcObservationContextInfo();
    }

    // Delegates to grpcInfo
    public String getServiceName() {
        return grpcInfo.getServiceName();
    }

    public String getMethodName() {
        return grpcInfo.getMethodName();
    }

    public String getFullMethodName() {
        return grpcInfo.getFullMethodName();
    }

    public MethodType getMethodType() {
        return grpcInfo.getMethodType();
    }

    @Nullable
    public Code getStatusCode() {
        return grpcInfo.getStatusCode();
    }

    public String getAuthority() {
        return grpcInfo.getAuthority();
    }

    public Metadata getHeaders() {
        return grpcInfo.getHeaders();
    }

    public Metadata getTrailers() {
        return grpcInfo.getTrailers();
    }

    public void setTrailers(Metadata trailers) {
        grpcInfo.setTrailers(trailers);
    }

    public void setServiceName(String serviceName) {
        this.grpcInfo.setServiceName(serviceName);
    }

    public void setMethodName(String methodName) {
        this.grpcInfo.setMethodName(methodName);
    }

    public void setFullMethodName(String fullMethodName) {
        this.grpcInfo.setFullMethodName(fullMethodName);
    }

    public void setMethodType(MethodType methodType) {
        this.grpcInfo.setMethodType(methodType);
    }

    public void setStatusCode(Code statusCode) {
        this.grpcInfo.setStatusCode(statusCode);
    }

    public void setAuthority(String authority) {
        this.grpcInfo.setAuthority(authority);
    }

    public void setHeaders(Metadata headers) {
        this.grpcInfo.setHeaders(headers);
    }

}
