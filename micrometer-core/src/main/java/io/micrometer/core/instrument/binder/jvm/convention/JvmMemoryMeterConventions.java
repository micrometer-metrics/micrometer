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

import io.micrometer.core.instrument.binder.MeterConvention;

import java.lang.management.MemoryPoolMXBean;

public interface JvmMemoryMeterConventions {

    MeterConvention<MemoryPoolMXBean> getMemoryUsedConvention();

    MeterConvention<MemoryPoolMXBean> getMemoryCommittedConvention();

    MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention();

}
