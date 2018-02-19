/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.samples.utils;

import io.micrometer.core.instrument.MeterRegistry;

// Run: git update-index --assume-unchanged **/SampleConfig.java
// If you legitimately need to change this file, you can undo this with: git update-index --no-assume-unchanged **/SampleConfig.java
public class SampleConfig {
    public static MeterRegistry myMonitoringSystem() {
        // Pick a monitoring system here to use in your samples.
        return SampleRegistries.wavefrontDirect("a6f74e29-7577-4b72-bef8-578f6053e908");
//        return SampleRegistries.graphite();
//        return SampleRegistries.signalFx("XNWd8jM0YiDPrroW3Ph0dw");
//        return SampleRegistries.jmx();
//        return SampleRegistries.jmx();
//        return SampleRegistries.prometheus();
//        return SampleRegistries.datadog("26ec541df8f1181b0bdc1bf51fde7cb2", "3288f53fc89a5cac3fb83e725f051f09fd6aba2e");
//        return SampleRegistries.newRelic("1799539", "1jyWDmrk5kswb-RBuLrhYkKQbBrn9_1Q");
    }
}
