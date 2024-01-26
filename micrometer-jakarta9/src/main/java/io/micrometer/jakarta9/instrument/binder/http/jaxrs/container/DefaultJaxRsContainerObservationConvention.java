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
package io.micrometer.jakarta9.instrument.binder.http.jaxrs.container;

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.http.AbstractDefaultHttpServerRequestObservationConvention;

/**
 * Default {@link JaxRsContainerObservationConvention}.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class DefaultJaxRsContainerObservationConvention extends AbstractDefaultHttpServerRequestObservationConvention
        implements JaxRsContainerObservationConvention {

    public static DefaultJaxRsContainerObservationConvention INSTANCE = new DefaultJaxRsContainerObservationConvention();

    private final String name;

    /**
     * Create a convention with the default name {@code "http.server.requests"}.
     */
    public DefaultJaxRsContainerObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultJaxRsContainerObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(JaxRsContainerObservationContext context) {
        return getContextualName(context.getCarrier().getMethod().toLowerCase(), context.getPathPattern());
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(JaxRsContainerObservationContext context) {
        String method = context.getCarrier() != null ? context.getCarrier().getMethod() : null;
        Integer status = context.getResponse() != null ? context.getResponse().getStatus() : null;
        String pathPattern = context.getPathPattern();
        String requestUri = context.getCarrier() != null ? context.getCarrier().getUriInfo().getRequestUri().toString()
                : null;
        return getLowCardinalityKeyValues(context.getError(), method, status, pathPattern, requestUri);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(JaxRsContainerObservationContext context) {
        String requestUri = context.getCarrier() != null ? context.getCarrier().getUriInfo().getRequestUri().toString()
                : null;
        String userAgent = context.getCarrier() != null ? context.getCarrier().getHeaderString("User-Agent") : null;
        return getHighCardinalityKeyValues(requestUri, userAgent);
    }

}
