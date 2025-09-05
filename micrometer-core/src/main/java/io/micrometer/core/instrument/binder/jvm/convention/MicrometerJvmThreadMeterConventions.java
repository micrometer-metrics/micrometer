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
package io.micrometer.core.instrument.binder.jvm.convention;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;

import java.util.Locale;

/**
 * Implementation that provides the historical convention used in Micrometer-provided
 * instrumentation.
 *
 * @since 1.16.0
 */
public class MicrometerJvmThreadMeterConventions implements JvmThreadMeterConventions {

    private final Tags extraTags;

    private final MeterConvention<Thread.State> threadCountConvention;

    public MicrometerJvmThreadMeterConventions(Tags extraTags) {
        this.extraTags = extraTags;
        this.threadCountConvention = new SimpleMeterConvention<>("jvm.threads.states", this::getStateTag);
    }

    private Tags getStateTag(Thread.State state) {
        return getCommonTags().and("state", state.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    protected Tags getCommonTags() {
        return extraTags;
    }

    @Override
    public MeterConvention<Thread.State> threadCountConvention() {
        return threadCountConvention;
    }

}
