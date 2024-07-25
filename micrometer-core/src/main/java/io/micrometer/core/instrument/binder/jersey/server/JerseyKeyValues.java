/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.common.KeyValue;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;

/**
 * Factory methods for {@link KeyValue KeyValues} associated with a request-response
 * exchange that is handled by Jersey server.
 */
@SuppressWarnings("deprecation")
class JerseyKeyValues {

    private static final KeyValue URI_NOT_FOUND = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.URI
        .withValue("NOT_FOUND");

    private static final KeyValue URI_REDIRECTION = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.URI
        .withValue("REDIRECTION");

    private static final KeyValue URI_ROOT = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.URI
        .withValue("root");

    private static final KeyValue URI_UNKNOWN = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.URI
        .withValue("UNKNOWN");

    private static final KeyValue EXCEPTION_NONE = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.EXCEPTION
        .withValue("None");

    private static final KeyValue STATUS_SERVER_ERROR = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.STATUS
        .withValue("500");

    private static final KeyValue METHOD_UNKNOWN = JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.METHOD
        .withValue("UNKNOWN");

    private JerseyKeyValues() {
    }

    /**
     * Creates a {@code method} KeyValue based on the {@link ContainerRequest#getMethod()
     * method} of the given {@code request}.
     * @param request the container request
     * @return the method KeyValue whose value is a capitalized method (e.g. GET).
     */
    static KeyValue method(ContainerRequest request) {
        return (request != null)
                ? JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.METHOD.withValue(request.getMethod())
                : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code status} KeyValue based on the status of the given
     * {@code response}.
     * @param response the container response
     * @return the status KeyValue derived from the status of the response
     */
    static KeyValue status(ContainerResponse response) {
        /* In case there is no response we are dealing with an unmapped exception. */
        return (response != null) ? JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.STATUS
            .withValue(Integer.toString(response.getStatus())) : STATUS_SERVER_ERROR;
    }

    /**
     * Creates a {@code uri} KeyValue based on the URI of the given {@code event}. Uses
     * the {@link ExtendedUriInfo#getMatchedTemplates()} if available. {@code REDIRECTION}
     * for 3xx responses, {@code NOT_FOUND} for 404 responses.
     * @param event the request event
     * @return the uri KeyValue derived from the request event
     */
    static KeyValue uri(RequestEvent event) {
        ContainerResponse response = event.getContainerResponse();
        if (response != null) {
            int status = response.getStatus();
            if (JerseyTags.isRedirection(status) && event.getUriInfo().getMatchedResourceMethod() == null) {
                return URI_REDIRECTION;
            }
            if (status == 404 && event.getUriInfo().getMatchedResourceMethod() == null) {
                return URI_NOT_FOUND;
            }
        }
        String matchingPattern = JerseyTags.getMatchingPattern(event);
        if (matchingPattern == null) {
            return URI_UNKNOWN;
        }
        if (matchingPattern.equals("/")) {
            return URI_ROOT;
        }
        return JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.URI.withValue(matchingPattern);
    }

    /**
     * Creates an {@code exception} KeyValue based on the {@link Class#getSimpleName()
     * simple name} of the class of the given {@code exception}.
     * @param event the request event
     * @return the exception KeyValue derived from the exception
     */
    static KeyValue exception(RequestEvent event) {
        Throwable exception = event.getException();
        if (exception == null) {
            return EXCEPTION_NONE;
        }
        ContainerResponse response = event.getContainerResponse();
        if (response != null) {
            int status = response.getStatus();
            if (status == 404 || JerseyTags.isRedirection(status)) {
                return EXCEPTION_NONE;
            }
        }
        if (exception.getCause() != null) {
            exception = exception.getCause();
        }
        String simpleName = exception.getClass().getSimpleName();
        return JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.EXCEPTION
            .withValue(StringUtils.isNotEmpty(simpleName) ? simpleName : exception.getClass().getName());
    }

    /**
     * Creates an {@code outcome} KeyValue based on the status of the given
     * {@code response}.
     * @param response the container response
     * @return the outcome KeyValue derived from the status of the response
     */
    static KeyValue outcome(ContainerResponse response) {
        if (response != null) {
            Outcome outcome = Outcome.forStatus(response.getStatus());
            return JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.OUTCOME.withValue(outcome.name());
        }
        /* In case there is no response we are dealing with an unmapped exception. */
        return JerseyObservationDocumentation.JerseyLegacyLowCardinalityTags.OUTCOME
            .withValue(Outcome.SERVER_ERROR.name());
    }

}
