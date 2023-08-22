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
package io.micrometer.jakarta.instrument.binder.tomcat;

import io.micrometer.jakarta.instrument.binder.http.DefaultHttpJakartaServerServletRequestObservationConvention;
import io.micrometer.jakarta.instrument.binder.http.HttpJakartaServerServletRequestObservationContext;
import io.micrometer.jakarta.instrument.binder.http.HttpJakartaServerServletRequestObservationConvention;
import io.micrometer.jakarta.instrument.binder.http.JakartaHttpObservationDocumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.ServletException;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;

/**
 * A {@link Valve} that creates {@link Observation}.
 *
 * @author Marcin Grzejszczak
 * @since 1.12.0
 * @see JakartaHttpObservationDocumentation
 */
public class ObservedValve extends ValveBase {

    private static final HttpJakartaServerServletRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultHttpJakartaServerServletRequestObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final HttpJakartaServerServletRequestObservationConvention observationConvention;

    public ObservedValve(ObservationRegistry observationRegistry,
            HttpJakartaServerServletRequestObservationConvention observationConvention) {
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
        HttpJakartaServerServletRequestObservationContext context = new HttpJakartaServerServletRequestObservationContext(
                request, response);
        observation = JakartaHttpObservationDocumentation.JAKARTA_SERVLET_SERVER_OBSERVATION
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
