/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.jakarta9.instrument.binder.http.servlet;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Context that holds information for metadata collection regarding Servlet HTTP requests
 * observations.
 * <p>
 * This context also extends {@link RequestReplyReceiverContext} for propagating tracing
 * information during HTTP request processing.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class HttpServletObservationContext
        extends RequestReplyReceiverContext<HttpServletRequest, HttpServletResponse> {

    @Nullable
    private String pathPattern;

    public HttpServletObservationContext(HttpServletRequest request, HttpServletResponse response) {
        super(HttpServletRequest::getHeader);
        setCarrier(request);
        setResponse(response);
    }

    @Nullable
    public String getPathPattern() {
        return this.pathPattern;
    }

    public void setPathPattern(@Nullable String pathPattern) {
        this.pathPattern = pathPattern;
    }

}
