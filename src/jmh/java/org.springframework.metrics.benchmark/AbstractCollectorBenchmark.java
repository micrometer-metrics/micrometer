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
package org.springframework.metrics.benchmark;

import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.DefaultCounterService;

class AbstractCollectorBenchmark {
    Registry spectatorRegistry = new DefaultRegistry();
    CounterService bootCounterService = new DefaultCounterService(new InMemoryMetricRepository());
    static io.prometheus.client.Counter prometheusCounter = io.prometheus.client.Counter
            .build("count", "Count nothing")
            .register();

    MetricRegistry dropwizardRegistry = new MetricRegistry();

    static final StatsDClient statsd = new NonBlockingStatsDClient(
            "prefix",
            "localhost",
            8125,
            "tag:value");
}
