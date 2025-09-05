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

public class MicrometerJvmMetersConventions implements JvmMetersConventions {

    @Override
    public JvmMemoryMeterConventions jvmMemoryMeterConventions(Tags extraTags) {
        return new MicrometerJvmMemoryMeterConventions(extraTags);
    }

    @Override
    public JvmClassLoadingMeterConventions jvmClassLoadingMeterConventions() {
        return new MicrometerJvmClassLoadingMeterConventions();
    }

    @Override
    public JvmThreadMeterConventions jvmThreadMeterConventions(Tags extraTags) {
        return new MicrometerJvmThreadMeterConventions(extraTags);
    }

    @Override
    public JvmCpuMeterConventions jvmCpuMeterConventions(Tags extraTags) {
        return new MicrometerJvmCpuMeterConventions(extraTags);
    }

}
