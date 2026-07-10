/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

public class NoopDistributionSummary extends NoopMeter implements DistributionSummary {

    public NoopDistributionSummary(Id id) {
        super(id);
    }

    @Override
    public void record(double amount) {
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public double totalAmount() {
        return 0;
    }

    @Override
    public double max() {
        return 0;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return HistogramSnapshot.empty(0, 0, 0);
    }

}
