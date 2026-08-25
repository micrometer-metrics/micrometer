/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * {@link MeterBinder} for JVM information.
 *
 * @author Erin Schnabel
 * @since 1.7.0
 */
public class JvmInfoMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("jvm.info", () -> 1L)
            .description("JVM version info")
            .tags("version", getSystemProperty("java.runtime.version", "unknown"), "vendor",
                    getSystemProperty("java.vm.vendor", "unknown"), "runtime",
                    getSystemProperty("java.runtime.name", "unknown"))
            .strongReference(true)
            .register(registry);
    }

    private static String getSystemProperty(String key, String defaultValue) {
        String value = System.getProperty(key, defaultValue);
        return value != null ? value : defaultValue;
    }

}
