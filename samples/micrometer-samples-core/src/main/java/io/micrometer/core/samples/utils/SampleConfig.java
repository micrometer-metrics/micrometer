/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.samples.utils;

import io.micrometer.core.instrument.MeterRegistry;

// Run: git update-index --assume-unchanged **/SampleConfig.java
// If you legitimately need to change this file, you can undo this with: git update-index --no-assume-unchanged **/SampleConfig.java
public class SampleConfig {

    public static MeterRegistry myMonitoringSystem() {
        // Pick a monitoring system here to use in your samples.
        return SampleRegistries.elastic();
    }

}
