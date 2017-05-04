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
package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * Trying to figure out how to get the experimental Spectator Atlas module publishing metrics
 */
class SpectatorAtlasTest2 {
    ManualClock clock = new ManualClock();

    AtlasRegistry registry = new AtlasRegistry(Clock.SYSTEM, new HashMap<String, String>() {{
        put("atlas.step", "PT10S");
        put("atlas.batchSize", "3");
    }}::get);

    @Test
    @Disabled
    void publishMetrics() {
        registry.start();
        registry.counter("myCounter");
    }
}
