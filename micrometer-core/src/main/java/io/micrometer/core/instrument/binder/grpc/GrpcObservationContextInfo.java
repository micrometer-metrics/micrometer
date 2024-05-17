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

/**
 * Holds information relevant to gRPC client and server observation contexts.
 */
public class GrpcObservationContextInfo {

    private String serviceName;

    private String methodName;

    private String fullMethodName;

    private MethodType methodType;

    private Code statusCode;

    private String authority;

    private Metadata headers;

    private Metadata trailers;

    public GrpcObservationContextInfo(String serviceName, String methodName, String fullMethodName,
            MethodType methodType, Code statusCode, String authority, Metadata headers, Metadata trailers) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.fullMethodName = fullMethodName;
        this.methodType = methodType;
        this.statusCode = statusCode;
        this.authority = authority;
        this.headers = headers;
        this.trailers = trailers;
    }

    public GrpcObservationContextInfo() {

    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFullMethodName() {
        return fullMethodName;
    }

    public MethodType getMethodType() {
        return methodType;
    }

    @Nullable
    public Code getStatusCode() {
        return statusCode;
    }

    public String getAuthority() {
        return authority;
    }

    public Metadata getHeaders() {
        return headers;
    }

    public Metadata getTrailers() {
        return trailers;
    }

    public void setTrailers(Metadata trailers) {
        this.trailers = trailers;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setFullMethodName(String fullMethodName) {
        this.fullMethodName = fullMethodName;
    }

    public void setMethodType(MethodType methodType) {
        this.methodType = methodType;
    }

    public void setStatusCode(Code statusCode) {
        this.statusCode = statusCode;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public void setHeaders(Metadata headers) {
        this.headers = headers;
    }

}
