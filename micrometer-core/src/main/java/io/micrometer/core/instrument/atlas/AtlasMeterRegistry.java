/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.NamingConvention;
import io.micrometer.core.instrument.spectator.step.StepSpectatorMeterRegistry;

/**
 * @author Jon Schneider
 */
public class AtlasMeterRegistry extends StepSpectatorMeterRegistry {
    public AtlasMeterRegistry(AtlasConfig config, Clock clock) {
        // The Spectator Atlas registry will do tag formatting for us, so we'll just pass through
        // tag keys and values with the identity formatter.
        super(new AtlasRegistry(new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }, config), clock, config.step().toMillis());

        // invalid character replacement happens in the spectator-reg-atlas module, so doesn't need
        // to be duplicated here.
        this.config().namingConvention(NamingConvention.camelCase);

        start();
    }

    public AtlasMeterRegistry(AtlasConfig config) {
        this(config, Clock.SYSTEM);
    }

    public void start() {
        getAtlasRegistry().start();
    }

    public void stop() {
        getAtlasRegistry().stop();
    }

    private AtlasRegistry getAtlasRegistry() {
        return (AtlasRegistry) this.getSpectatorRegistry();
    }
}
