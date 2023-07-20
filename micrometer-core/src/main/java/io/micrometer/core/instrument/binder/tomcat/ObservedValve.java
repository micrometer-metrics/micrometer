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
package io.micrometer.core.instrument.binder.tomcat;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import io.micrometer.core.instrument.binder.http.DefaultHttpServerRequestObservationConvention;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation;
import io.micrometer.core.instrument.binder.http.HttpServerRequestObservationContext;
import io.micrometer.core.instrument.binder.http.HttpServerRequestObservationConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * A {@link Valve} that creates {@link Observation}.
 *
 * @author Marcin Grzejszczak
 * @since 1.12.0
 * @see HttpObservationDocumentation
 */
public class ObservedValve extends ValveBase {

    private static final HttpServerRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultHttpServerRequestObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final HttpServerRequestObservationConvention observationConvention;

    public ObservedValve(ObservationRegistry observationRegistry,
            HttpServerRequestObservationConvention observationConvention) {
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
        setAsyncSupported(true);
    }

    public ObservedValve(ObservationRegistry observationRegistry) {
        this(observationRegistry, null);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        Observation observation = (Observation) request.getAttribute(Observation.class.getName());
        if (observation != null) {
            // this could happen for async dispatch
            try (Observation.Scope scope = observation.openScope()) {
                Valve next = getNext();
                if (null == next) {
                    // no next valve
                    return;
                }
                next.invoke(request, response);
                return;
            }
        }
        HttpServerRequestObservationContext context = new HttpServerRequestObservationContext(request, response);
        observation = HttpObservationDocumentation.SERVER_OBSERVATION
            .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> context,
                    this.observationRegistry)
            .start();
        request.setAttribute(Observation.class.getName(), observation);
        try (Observation.Scope scope = observation.openScope()) {
            Valve next = getNext();
            if (null == next) {
                // no next valve
                return;
            }
            next.invoke(request, response);
        }
        catch (Exception exception) {
            observation.error(exception);
            throw exception;
        }
        finally {
            observation.stop();
        }
    }

}
