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

    private String serviceName;

    private String methodName;

    private String fullMethodName;

    private MethodType methodType;

    @Nullable
    private Code statusCode;

    @Nullable
    private String authority;

    private Metadata headers;

    private Metadata trailers;

    private boolean cancelled;

    @Nullable
    private String peerName;

    @Nullable
    private Integer peerPort;

    public GrpcServerObservationContext(Getter<Metadata> getter) {
        super(getter);
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getFullMethodName() {
        return this.fullMethodName;
    }

    public void setFullMethodName(String fullMethodName) {
        this.fullMethodName = fullMethodName;
    }

    public MethodType getMethodType() {
        return this.methodType;
    }

    public void setMethodType(MethodType methodType) {
        this.methodType = methodType;
    }

    @Nullable
    public Code getStatusCode() {
        return this.statusCode;
    }

    public void setStatusCode(Code statusCode) {
        this.statusCode = statusCode;
    }

    @Nullable
    public String getAuthority() {
        return this.authority;
    }

    public void setAuthority(@Nullable String authority) {
        this.authority = authority;
    }

    /**
     * Response headers.
     * @return response headers
     * @since 1.13.0
     */
    public Metadata getHeaders() {
        return this.headers;
    }

    /**
     * Set response headers.
     * @param headers response headers
     * @since 1.13.0
     */
    public void setHeaders(Metadata headers) {
        this.headers = headers;
    }

    /**
     * Trailers.
     * @return trailers
     * @since 1.13.0
     */
    public Metadata getTrailers() {
        return this.trailers;
    }

    /**
     * Set trailers.
     * @param trailers
     * @since 1.13.0
     */
    public void setTrailers(Metadata trailers) {
        this.trailers = trailers;
    }

    /**
     * Indicate whether the request is cancelled or not.
     * @return {@code true} if the request is cancelled
     * @since 1.14
     */
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Set {@code true} when the request is cancelled.
     * @since 1.14
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Nullable
    public String getPeerName() {
        return this.peerName;
    }

    public void setPeerName(@Nullable String peerName) {
        this.peerName = peerName;
    }

    @Nullable
    public Integer getPeerPort() {
        return this.peerPort;
    }

    public void setPeerPort(@Nullable Integer peerPort) {
        this.peerPort = peerPort;
    }

}
