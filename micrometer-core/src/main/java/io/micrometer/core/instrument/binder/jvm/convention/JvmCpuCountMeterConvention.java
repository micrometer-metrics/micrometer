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
import org.jspecify.annotations.Nullable;

/**
 * {@link MeterConvention} for CPU count metrics.
 *
 * @see io.micrometer.core.instrument.binder.system.ProcessorMetrics
 * @since 1.16.0
 */
public interface JvmCpuCountMeterConvention extends MeterConvention<@Nullable Object> {

    /**
     * Create a {@link JvmCpuCountMeterConvention} with the given name.
     * @param name meter name
     * @return a new convention instance
     */
    static JvmCpuCountMeterConvention of(String name) {
        return () -> name;
    }

    /**
     * Create a {@link JvmCpuCountMeterConvention} with the given name and tags.
     * @param name meter name
     * @param tags tags for the meter
     * @return a new convention instance
     */
    static JvmCpuCountMeterConvention of(String name, Tags tags) {
        return new JvmCpuCountMeterConvention() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Tags getTags(@Nullable Object context) {
                return tags;
            }
        };
    }

}
