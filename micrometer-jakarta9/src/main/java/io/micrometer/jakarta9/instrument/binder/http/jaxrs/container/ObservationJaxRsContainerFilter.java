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

import io.micrometer.common.lang.Nullable;
import io.micrometer.jakarta9.instrument.binder.http.JakartaHttpObservationDocumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class ObservationJaxRsContainerFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String OBSERVATION_PROPERTY = ObservationJaxRsContainerFilter.class.getName() + ".observation";

    private static final String OBSERVATION_SCOPE_PROPERTY = ObservationJaxRsContainerFilter.class.getName()
            + ".observationScope";

    private static final String OBSERVATION_FINISHED_PROPERTY = ObservationJaxRsContainerFilter.class.getName()
            + ".observationFinished";

    @Nullable
    private final String name;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final JaxRsContainerObservationConvention convention;

    /**
     * Creates a new instance of the filter.
     * @param name optional observation name
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationJaxRsContainerFilter(@Nullable String name, ObservationRegistry observationRegistry,
            @Nullable JaxRsContainerObservationConvention convention) {
        this.name = name;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    /**
     * Creates a new instance of the filter.
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationJaxRsContainerFilter(ObservationRegistry observationRegistry,
            @Nullable JaxRsContainerObservationConvention convention) {
        this(null, observationRegistry, convention);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        JaxRsContainerObservationContext context = new JaxRsContainerObservationContext(requestContext);
        Observation observation = JakartaHttpObservationDocumentation.JAX_RS_CONTAINER_OBSERVATION.start(convention,
                defaultConvention(), () -> context, observationRegistry);
        requestContext.setProperty(OBSERVATION_PROPERTY, observation);
        requestContext.setProperty(OBSERVATION_SCOPE_PROPERTY, observation.openScope());
    }

    private DefaultJaxRsContainerObservationConvention defaultConvention() {
        return this.name != null ? new DefaultJaxRsContainerObservationConvention(this.name)
                : DefaultJaxRsContainerObservationConvention.INSTANCE;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Observation observation = (Observation) requestContext.getProperty(OBSERVATION_PROPERTY);
        if (observation == null) {
            return;
        }
        if (requestContext.getProperty(OBSERVATION_FINISHED_PROPERTY) != null) {
            // For streaming this can be called multiple times
            return;
        }
        switch (responseContext.getStatusInfo().getFamily()) {
            case INFORMATIONAL:
            case SUCCESSFUL:
            case REDIRECTION:
            case OTHER:
                // do nothing for successful (and unknown) responses
                break;
            case CLIENT_ERROR:
            case SERVER_ERROR:
                observation.error(new IOException("An exception happened"));
                break;
            default:
                break;
        }
        requestContext.setProperty(OBSERVATION_FINISHED_PROPERTY, true);
        Observation.Scope scope = (Scope) requestContext.getProperty(OBSERVATION_SCOPE_PROPERTY);
        if (scope != null) {
            scope.close();
        }
        JaxRsContainerObservationContext context = (JaxRsContainerObservationContext) observation.getContext();
        context.setResponse(responseContext);
        observation.stop();
    }

}
