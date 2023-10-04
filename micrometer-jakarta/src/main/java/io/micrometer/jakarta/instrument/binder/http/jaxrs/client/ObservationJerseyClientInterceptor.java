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
package io.micrometer.jakarta.instrument.binder.http.jaxrs.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import org.glassfish.jersey.client.spi.PostInvocationInterceptor;

import static io.micrometer.jakarta.instrument.binder.http.jaxrs.client.ObservationJaxRsHttpClientFilter.OBSERVATION_PROPERTY;
import static io.micrometer.jakarta.instrument.binder.http.jaxrs.client.ObservationJaxRsHttpClientFilter.OBSERVATION_SCOPE_PROPERTY;

/**
 * A post-invocation client interceptor that will stop an observation started in
 * {@link ObservationJaxRsHttpClientFilter} in case of exceptions.
 *
 * @author Marcin Grzejszczak
 * @since 1.12.0
 * @see ObservationJaxRsHttpClientFilter
 */
public class ObservationJerseyClientInterceptor implements PostInvocationInterceptor {

    @Override
    public void afterRequest(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        // no-op
    }

    @Override
    public void onException(ClientRequestContext requestContext, ExceptionContext exceptionContext) {
        Observation observation = (Observation) requestContext.getProperty(OBSERVATION_PROPERTY);
        Observation.Scope observationScope = (Scope) requestContext.getProperty(OBSERVATION_SCOPE_PROPERTY);
        if (observation == null) {
            return;
        }
        observationScope.close();
        Throwable thrown = exceptionContext.getThrowables().peek();
        if (thrown != null) {
            observation.error(thrown);
        }
        requestContext.removeProperty(OBSERVATION_PROPERTY);
        requestContext.removeProperty(OBSERVATION_SCOPE_PROPERTY);
    }

}
