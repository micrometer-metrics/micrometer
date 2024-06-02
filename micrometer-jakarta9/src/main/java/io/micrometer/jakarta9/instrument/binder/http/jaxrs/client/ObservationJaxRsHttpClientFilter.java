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
package io.micrometer.jakarta9.instrument.binder.http.jaxrs.client;

import io.micrometer.common.lang.Nullable;
import io.micrometer.jakarta9.instrument.binder.http.JakartaHttpObservationDocumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;

import java.io.IOException;

/**
 * A {@link ClientResponseFilter} and {@link ClientResponseFilter} that is an around
 * wrapper over sending an HTTP call. This interceptor does not handle exceptions.
 *
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class ObservationJaxRsHttpClientFilter implements ClientRequestFilter, ClientResponseFilter {

    static final String OBSERVATION_PROPERTY = ObservationJaxRsHttpClientFilter.class.getName() + ".observation";

    static final String OBSERVATION_SCOPE_PROPERTY = ObservationJaxRsHttpClientFilter.class.getName()
            + ".observationScope";

    @Nullable
    private final String name;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final JaxRsHttpClientObservationConvention convention;

    /**
     * Creates a new instance of the filter.
     * @param name optional observation name
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationJaxRsHttpClientFilter(@Nullable String name, ObservationRegistry observationRegistry,
            @Nullable JaxRsHttpClientObservationConvention convention) {
        this.name = name;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    /**
     * Creates a new instance of the filter.
     * @param observationRegistry observation registry
     * @param convention optional convention
     */
    public ObservationJaxRsHttpClientFilter(ObservationRegistry observationRegistry,
            @Nullable JaxRsHttpClientObservationConvention convention) {
        this(null, observationRegistry, convention);
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        JaxRsHttpClientObservationContext context = new JaxRsHttpClientObservationContext(requestContext);
        Observation observation = JakartaHttpObservationDocumentation.JAX_RS_CLIENT_OBSERVATION.start(convention,
                defaultConvention(), () -> context, observationRegistry);
        requestContext.setProperty(OBSERVATION_PROPERTY, observation);
        requestContext.setProperty(OBSERVATION_SCOPE_PROPERTY, observation.openScope());
    }

    private DefaultJaxRsHttpClientObservationConvention defaultConvention() {
        return this.name != null ? new DefaultJaxRsHttpClientObservationConvention(this.name)
                : DefaultJaxRsHttpClientObservationConvention.INSTANCE;
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Observation observation = (Observation) requestContext.getProperty(OBSERVATION_PROPERTY);
        if (observation == null) {
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
        Observation.Scope scope = (Observation.Scope) requestContext.getProperty(OBSERVATION_SCOPE_PROPERTY);
        if (scope != null) {
            scope.close();
        }
        JaxRsHttpClientObservationContext context = (JaxRsHttpClientObservationContext) observation.getContext();
        context.setResponse(responseContext);
        observation.stop();
    }

}
