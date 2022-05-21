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
package io.micrometer.boot2.samples;

import io.micrometer.boot2.samples.components.PersonController;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = PersonController.class)
@EnableScheduling
public class DynatraceSampleV1 {

    public static void main(String[] args) {
        // WARNING:
        // This example uses the Dynatrace v1 registry, which uses the former Timeseries
        // v1 API.
        // It is suggested to use the current v2 registry instead, which exports to the
        // Metrics API v2.
        new SpringApplicationBuilder(DynatraceSampleV1.class).profiles("dynatrace-v1").run(args);
    }

}
