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

public class MicrometerJvmCpuMeterConventions implements JvmCpuMeterConventions {

    private final Tags extraTags;

    public MicrometerJvmCpuMeterConventions(Tags extraTags) {
        this.extraTags = extraTags;
    }

    protected Tags getCommonTags() {
        return extraTags;
    }

    @Override
    public MeterConvention<Object> cpuTimeConvention() {
        return new SimpleMeterConvention<>("process.cpu.time", getCommonTags());
    }

    @Override
    public MeterConvention<Object> cpuCountConvention() {
        return new SimpleMeterConvention<>("system.cpu.count", getCommonTags());
    }

    @Override
    public MeterConvention<Object> processCpuLoadConvention() {
        return new SimpleMeterConvention<>("process.cpu.usage", getCommonTags());
    }

}
