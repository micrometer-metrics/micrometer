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
package io.micrometer.spring.samples;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.core.instrument.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.atlas.AtlasUtils;

/**
 * Demonstrates how to push metrics to Atlas explicitly. This is useful
 * for short-lived batch applications.
 *
 * @author Jon Schneider
 */
public class AtlasPushSample {
    public static void main(String[] args) {
        AtlasConfig config = AtlasUtils.pushConfig("http://localhost:7101/api/v1/publish");
        AtlasMeterRegistry registry = new AtlasMeterRegistry(config);
        registry.counter("push_counter").increment();

        // push metric and block until completion
        AtlasUtils.atlasPublish(registry, config);
    }
}

