/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.observation.transport.http.convention;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.http.context.HttpServerContext;

/**
 * Conventions for HTTP server observation names implemented with OpenTelemetry.
 *
 * @author Marcin Grzejsczak
 * @since 1.10.0
 */
public class OpenTelemetryHttpServerSemanticNameProvider implements Observation.ContextAwareSemanticNameProvider {
    private final ObservationRegistry.ObservationNamingConfiguration namingConfiguration;

    public OpenTelemetryHttpServerSemanticNameProvider(ObservationRegistry.ObservationNamingConfiguration namingConfiguration) {
        this.namingConfiguration = namingConfiguration;
    }

    @Override
    public String getName() {
        return "http.server.duration";
    }

    @Override
    public boolean isApplicable(Observation.Context object) {
        if (this.namingConfiguration == ObservationRegistry.ObservationNamingConfiguration.STANDARDIZED) {
            return false;
        }
        return object instanceof HttpServerContext;
    }
}
