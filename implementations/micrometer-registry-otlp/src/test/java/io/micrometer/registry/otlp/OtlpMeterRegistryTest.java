/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class OtlpMeterRegistryTest {

    OtlpMeterRegistry registry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM);

    @Test
    void sendSomeData() {
        Counter counter = registry.counter("log.event");
        counter.increment();
        counter.increment();
        Timer timer = Timer.builder("http.client.requests").publishPercentileHistogram().description("timing http client requests").register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        registry.publish();
    }
}
