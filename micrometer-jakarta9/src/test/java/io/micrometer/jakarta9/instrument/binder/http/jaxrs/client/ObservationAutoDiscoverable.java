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

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.FeatureContext;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;

/**
 * Example of binding observation into Jersey.
 */
@ConstrainedTo(RuntimeType.CLIENT)
public class ObservationAutoDiscoverable implements AutoDiscoverable {

    static final int CLIENT_OBSERVABILITY_PRIORITY = 20;

    @Override
    public void configure(FeatureContext context) {
        if (!context.getConfiguration().isRegistered(ObservationJaxRsHttpClientFilter.class)) {
            context.register(ObservationJaxRsHttpClientFilter.class, CLIENT_OBSERVABILITY_PRIORITY);
            context.register(ObservationJerseyClientInterceptor.class, CLIENT_OBSERVABILITY_PRIORITY);
        }
    }

}
