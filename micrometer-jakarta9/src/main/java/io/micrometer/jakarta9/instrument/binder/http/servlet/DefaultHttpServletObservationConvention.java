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

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.http.AbstractDefaultHttpServerRequestObservationConvention;

/**
 * Default {@link HttpServletObservationConvention}.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class DefaultHttpServletObservationConvention extends AbstractDefaultHttpServerRequestObservationConvention
        implements HttpServletObservationConvention {

    /**
     * Default instance.
     */
    public static final DefaultHttpServletObservationConvention INSTANCE = new DefaultHttpServletObservationConvention();

    private final String name;

    /**
     * Create a convention with the default name {@code "http.server.requests"}.
     */
    public DefaultHttpServletObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultHttpServletObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(HttpServletObservationContext context) {
        String method = context.getCarrier().getMethod();
        if (method == null) {
            return null;
        }
        return getContextualName(method.toLowerCase(), context.getPathPattern());
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpServletObservationContext context) {
        String method = context.getCarrier() != null ? context.getCarrier().getMethod() : null;
        Integer status = context.getResponse() != null ? context.getResponse().getStatus() : null;
        String pathPattern = context.getPathPattern();
        String requestUri = context.getCarrier() != null ? context.getCarrier().getRequestURI() : null;
        return getLowCardinalityKeyValues(context.getError(), method, status, pathPattern, requestUri);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(HttpServletObservationContext context) {
        String requestUri = context.getCarrier() != null ? context.getCarrier().getRequestURI() : null;
        String userAgent = context.getCarrier() != null ? context.getCarrier().getHeader("User-Agent") : null;
        return getHighCardinalityKeyValues(requestUri, userAgent);
    }

}
