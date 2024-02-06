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

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;

/**
 * Default implementation for {@link JerseyObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public class DefaultJerseyObservationConvention implements JerseyObservationConvention {

    private final String metricsName;

    public DefaultJerseyObservationConvention(String metricsName) {
        this.metricsName = metricsName;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(JerseyContext context) {
        RequestEvent event = context.getRequestEvent();
        ContainerRequest request = context.getCarrier();
        ContainerResponse response = context.getResponse();
        return KeyValues.of(JerseyKeyValues.method(request), JerseyKeyValues.uri(event),
                JerseyKeyValues.exception(event), JerseyKeyValues.status(response), JerseyKeyValues.outcome(response));
    }

    @Override
    public String getName() {
        return this.metricsName;
    }

    @Nullable
    @Override
    public String getContextualName(JerseyContext context) {
        if (context.getCarrier() == null) {
            return null;
        }
        return "HTTP " + context.getCarrier().getMethod();
    }

}
