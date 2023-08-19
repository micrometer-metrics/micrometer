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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} for Apache HTTP Client 5 instrumentation.
 *
 * @since 1.11.0
 * @see DefaultApacheHttpClientObservationConvention
 */
public interface ApacheHttpClientObservationConvention extends ObservationConvention<ApacheHttpClientContext> {

    /**
     * Name of the {@link org.apache.hc.client5.http.protocol.HttpClientContext} attribute
     * that should hold the String representation of the URI template used for creating
     * the client URL.
     * <p>
     * This value can be contributed as an {@link io.micrometer.common.KeyValue} to the
     * recorded observations. <pre>
     * String uriTemplate = "/users/{id}";
     * HttpClientContext clientContext = ...
     * clientContext.setAttribute(ApacheHttpClientObservationConvention.URI_TEMPLATE_ATTRIBUTE, uriTemplate);
     * </pre>
     * @since 1.12.0
     */
    String URI_TEMPLATE_ATTRIBUTE = "micrometer.uri.template";

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof ApacheHttpClientContext;
    }

}
