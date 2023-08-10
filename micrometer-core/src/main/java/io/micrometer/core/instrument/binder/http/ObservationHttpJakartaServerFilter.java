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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class ObservationHttpJakartaServerFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Nullable
    private final String name;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final HttpJakartaServerRequestObservationConvention convention;

    /**
     * Creates a new instance of the filter.
     * @param name optional observation name
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationHttpJakartaServerFilter(@Nullable String name, ObservationRegistry observationRegistry,
            @Nullable HttpJakartaServerRequestObservationConvention convention) {
        this.name = name;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    /**
     * Creates a new instance of the filter.
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationHttpJakartaServerFilter(ObservationRegistry observationRegistry,
            @Nullable HttpJakartaServerRequestObservationConvention convention) {
        this(null, observationRegistry, convention);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        HttpJakartaServerRequestObservationContext context = new HttpJakartaServerRequestObservationContext(
                requestContext);
        Observation observation = HttpObservationDocumentation.JAKARTA_SERVER_OBSERVATION.start(convention,
                defaultConvention(), () -> context, observationRegistry);
        requestContext.setProperty(ObservationHttpJakartaServerFilter.class.getName(), observation);
    }

    private DefaultHttpJakartaServerRequestObservationConvention defaultConvention() {
        return this.name != null ? new DefaultHttpJakartaServerRequestObservationConvention(this.name)
                : DefaultHttpJakartaServerRequestObservationConvention.INSTANCE;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Observation observation = (Observation) requestContext
            .getProperty(ObservationHttpJakartaServerFilter.class.getName());
        if (observation == null) {
            return;
        }
        HttpJakartaServerRequestObservationContext context = (HttpJakartaServerRequestObservationContext) observation
            .getContext();
        context.setResponse(responseContext);
        observation.stop();
    }

}
