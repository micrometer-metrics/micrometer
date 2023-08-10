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
package io.micrometer.core.instrument.binder.http;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.transport.RequestReplySenderContext;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;

/**
 * Context that holds information for metadata collection during the client HTTP exchanges
 * observations.
 * <p>
 * This context also extends {@link RequestReplySenderContext} for propagating tracing
 * information with the HTTP client exchange.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.12.0
 */
public class HttpJakartaClientRequestObservationContext
        extends RequestReplySenderContext<ClientRequestContext, ClientResponseContext> {

    @Nullable
    private String uriTemplate;

    /**
     * Create an observation context for HTTP client observations.
     * @param containerRequestContext the context for a {@link ClientRequestContext}
     */
    public HttpJakartaClientRequestObservationContext(ClientRequestContext containerRequestContext) {
        super(HttpJakartaClientRequestObservationContext::setRequestHeader);
        this.setCarrier(containerRequestContext);
    }

    private static void setRequestHeader(@Nullable ClientRequestContext context, String name, String value) {
        if (context != null) {
            context.getHeaders().add(name, value);
        }
    }

    /**
     * Return the URI template used for the current client exchange, {@code null} if none
     * was used.
     */
    @Nullable
    public String getUriTemplate() {
        return this.uriTemplate;
    }

    /**
     * Set the URI template used for the current client exchange.
     */
    public void setUriTemplate(@Nullable String uriTemplate) {
        this.uriTemplate = uriTemplate;
    }

}
