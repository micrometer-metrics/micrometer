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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * {@link RequestEventListener} recording observations for Jersey server requests.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public class ObservationRequestEventListener implements RequestEventListener {

    private final Map<ContainerRequest, ObservationScopeAndContext> observations = Collections
        .synchronizedMap(new IdentityHashMap<>());

    private final ObservationRegistry registry;

    private final JerseyObservationConvention customConvention;

    private final String metricName;

    private final JerseyObservationConvention defaultConvention;

    public ObservationRequestEventListener(ObservationRegistry registry, String metricName) {
        this(registry, metricName, null);
    }

    public ObservationRequestEventListener(ObservationRegistry registry, String metricName,
            JerseyObservationConvention customConvention) {
        this.registry = requireNonNull(registry);
        this.metricName = requireNonNull(metricName);
        this.customConvention = customConvention;
        this.defaultConvention = new DefaultJerseyObservationConvention(this.metricName);
    }

    @Override
    public void onEvent(RequestEvent event) {
        ContainerRequest containerRequest = event.getContainerRequest();

        switch (event.getType()) {
            case ON_EXCEPTION:
                if (!isNotFoundException(event)) {
                    break;
                }
            case REQUEST_MATCHED:
                JerseyContext jerseyContext = new JerseyContext(event);
                Observation observation = JerseyObservationDocumentation.DEFAULT.start(this.customConvention,
                        this.defaultConvention, () -> jerseyContext, this.registry);
                Observation.Scope scope = observation.openScope();
                observations.put(event.getContainerRequest(), new ObservationScopeAndContext(scope, jerseyContext));
                break;
            case RESP_FILTERS_START:
                ObservationScopeAndContext observationScopeAndContext = observations.get(containerRequest);
                if (observationScopeAndContext != null) {
                    observationScopeAndContext.jerseyContext.setResponse(event.getContainerResponse());
                    observationScopeAndContext.jerseyContext.setRequestEvent(event);
                }
                break;
            case FINISHED:
                ObservationScopeAndContext finishedObservation = observations.remove(containerRequest);
                if (finishedObservation != null) {
                    finishedObservation.jerseyContext.setRequestEvent(event);
                    Observation.Scope observationScope = finishedObservation.observationScope;
                    observationScope.close();
                    observationScope.getCurrentObservation().stop();
                }
                break;
            default:
                break;
        }
    }

    private boolean isNotFoundException(RequestEvent event) {
        Throwable t = event.getException();
        if (t == null) {
            return false;
        }
        String className = t.getClass().getCanonicalName();
        return className.equals("jakarta.ws.rs.NotFoundException") || className.equals("javax.ws.rs.NotFoundException");
    }

    private static class ObservationScopeAndContext {

        final Observation.Scope observationScope;

        final JerseyContext jerseyContext;

        ObservationScopeAndContext(Observation.Scope observationScope, JerseyContext jerseyContext) {
            this.observationScope = observationScope;
            this.jerseyContext = jerseyContext;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObservationScopeAndContext that = (ObservationScopeAndContext) o;
            return Objects.equals(observationScope, that.observationScope)
                    && Objects.equals(jerseyContext, that.jerseyContext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observationScope, jerseyContext);
        }

    }

}
