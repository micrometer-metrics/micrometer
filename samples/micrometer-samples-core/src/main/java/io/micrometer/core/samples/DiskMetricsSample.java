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
package io.micrometer.core.samples;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * Demonstrates how to provide a file property to the disk space metrics monitor.
 */
public class DiskMetricsSample {

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        new DiskSpaceMetrics(new File(System.getProperty("user.dir"))).bindTo(registry);

        Flux.never().blockLast();
    }

}
