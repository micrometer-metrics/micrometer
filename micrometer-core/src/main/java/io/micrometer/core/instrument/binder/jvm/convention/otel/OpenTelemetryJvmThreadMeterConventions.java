/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm.convention.otel;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmThreadMeterConventions;

import java.util.Locale;

/**
 * Conventions for JVM thread metrics based on OpenTelemetry semantic conventions.
 *
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/runtime/jvm-metrics.md">OpenTelemtry
 * Semantic conventions for JVM metrics v1.37.0</a>
 * @see io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
 * @since 1.16.0
 */
public class OpenTelemetryJvmThreadMeterConventions extends MicrometerJvmThreadMeterConventions {

    private final MeterConvention<Thread.State> threadCountConvention;

    public OpenTelemetryJvmThreadMeterConventions(Tags extraTags) {
        super(extraTags);
        threadCountConvention = new SimpleMeterConvention<>("jvm.thread.count", this::getThreadStateTags);
    }

    private Tags getThreadStateTags(Thread.State state) {
        return getCommonTags().and("jvm.thread.state", state.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public MeterConvention<Thread.State> threadCountConvention() {
        return this.threadCountConvention;
    }

}
