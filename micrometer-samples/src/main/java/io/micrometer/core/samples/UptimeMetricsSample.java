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
package io.micrometer.core.samples;

import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.samples.utils.SampleRegistries;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.PushGateway;

import java.io.IOException;

/**
 * Sample to diagnose issue #243
 */
public class UptimeMetricsSample {
    public static void main(String[] args) throws IOException {
        PrometheusMeterRegistry registry = SampleRegistries.prometheusPushgateway();
        registry.config().commonTags("instance", "sample-host");
        new UptimeMetrics().bindTo(registry);
        new PushGateway("localhost:9091").pushAdd(registry.getPrometheusRegistry(), "samples");
    }
}
