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

import io.micrometer.observation.ObservationRegistry;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import static java.util.Objects.requireNonNull;

/**
 * The Micrometer {@link ApplicationEventListener} which registers
 * {@link RequestEventListener} for instrumenting Jersey server requests with
 * observations.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public class ObservationApplicationEventListener implements ApplicationEventListener {

    private final ObservationRegistry observationRegistry;

    private final String metricName;

    private final JerseyObservationConvention jerseyObservationConvention;

    public ObservationApplicationEventListener(ObservationRegistry observationRegistry, String metricName) {
        this(observationRegistry, metricName, null);
    }

    public ObservationApplicationEventListener(ObservationRegistry observationRegistry, String metricName,
            JerseyObservationConvention jerseyObservationConvention) {
        this.observationRegistry = requireNonNull(observationRegistry);
        this.metricName = requireNonNull(metricName);
        this.jerseyObservationConvention = jerseyObservationConvention;
    }

    @Override
    public void onEvent(ApplicationEvent event) {
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return new ObservationRequestEventListener(observationRegistry, metricName, jerseyObservationConvention);
    }

}
