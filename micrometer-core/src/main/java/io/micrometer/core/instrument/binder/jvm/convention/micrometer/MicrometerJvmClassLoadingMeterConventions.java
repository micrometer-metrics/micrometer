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
package io.micrometer.core.instrument.binder.jvm.convention.micrometer;

import io.micrometer.core.instrument.Tags;

/**
 * Micrometer's historical conventions for JVM class loading metrics.
 *
 * @see io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
 * @since 1.16.0
 * @deprecated use individual convention types such as
 * {@link MicrometerJvmClassCountMeterConvention},
 * {@link MicrometerJvmClassLoadedMeterConvention}, and
 * {@link MicrometerJvmClassUnloadedMeterConvention} instead
 */
@Deprecated
@SuppressWarnings("deprecation")
public class MicrometerJvmClassLoadingMeterConventions
        implements io.micrometer.core.instrument.binder.jvm.convention.JvmClassLoadingMeterConventions {

    private final Tags extraTags;

    public MicrometerJvmClassLoadingMeterConventions() {
        this(Tags.empty());
    }

    public MicrometerJvmClassLoadingMeterConventions(Tags extraTags) {
        this.extraTags = extraTags;
    }

    protected Tags getCommonTags() {
        return extraTags;
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<Object> loadedConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.classes.loaded.count",
                getCommonTags());
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<Object> unloadedConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.classes.unloaded",
                getCommonTags());
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<Object> currentClassCountConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.classes.loaded", getCommonTags());
    }

}
