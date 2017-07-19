/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.stats.quantile;

import java.util.Collection;

/**
 * Calculate φ-quantiles, where 0 ≤ φ ≤ 1. The φ-quantile is the observation value that ranks at number φ*N among
 * N observations. Examples for φ-quantiles: The 0.5-quantile is known as the median. The 0.95-quantile is the
 * 95th percentile.
 *
 * @author Jon Schneider
 */
public interface Quantiles {
    /**
     * Add a sample
     * @param value
     */
    void observe(double value);
    
    /**
     * @param percentile (0 .. 1.0)
     * @return Get the Nth percentile
     */
    Double get(double percentile);

    /**
     * Get all monitored quantiles
     */
    Collection<Double> monitored();
}