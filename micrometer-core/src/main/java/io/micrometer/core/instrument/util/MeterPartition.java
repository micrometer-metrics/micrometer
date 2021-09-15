/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.util;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

/**
 * {@link AbstractPartition} for {@link Meter}.
 *
 * @author Jon Schneider
 */
public class MeterPartition extends AbstractPartition<Meter> {
    /**
     * Create a {@code MeterPartition} instance.
     *
     * @param meters meters to partition
     * @param partitionSize partition size
     * @since 1.8.0
     */
    public MeterPartition(List<Meter> meters, int partitionSize) {
        super(meters, partitionSize);
    }

    public MeterPartition(MeterRegistry registry, int partitionSize) {
        this(registry.getMeters(), partitionSize);
    }

    public static List<List<Meter>> partition(MeterRegistry registry, int partitionSize) {
        return new MeterPartition(registry, partitionSize);
    }
}
