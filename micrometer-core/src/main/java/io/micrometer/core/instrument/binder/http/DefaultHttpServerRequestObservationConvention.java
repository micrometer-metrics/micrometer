/*
 * Copyright 2020 VMware, Inc.
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

import io.micrometer.common.KeyValues;

/**
 * Default {@link HttpJakartaServerRequestObservationConvention}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class DefaultHttpServerRequestObservationConvention extends AbstractDefaultHttpServerRequestObservationConvention
        implements HttpServerRequestObservationConvention {

    private static final String DEFAULT_NAME = "http.server.requests";

    private final String name;

    /**
     * Create a convention with the default name {@code "http.server.requests"}.
     */
    public DefaultHttpServerRequestObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultHttpServerRequestObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(HttpServerRequestObservationContext context) {
        String method = context.getCarrier().getMethod();
        if (method == null) {
            return null;
        }
        return getContextualName(method.toLowerCase(), context.getPathPattern());
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpServerRequestObservationContext context) {
        String method = context.getCarrier() != null ? context.getCarrier().getMethod() : null;
        Integer status = context.getResponse() != null ? context.getResponse().getStatus() : null;
        String pathPattern = context.getPathPattern();
        String requestUri = context.getCarrier() != null ? context.getCarrier().getRequestURI() : null;
        return getLowCardinalityKeyValues(context.getError(), method, status, pathPattern, requestUri);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(HttpServerRequestObservationContext context) {
        String requestUri = context.getCarrier() != null ? context.getCarrier().getRequestURI() : null;
        return getHighCardinalityKeyValues(requestUri);
    }

}
