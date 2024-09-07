/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.prometheusmetrics;

import io.prometheus.metrics.core.exemplars.ExemplarSampler;

/**
 * A factory that creates {@link ExemplarSampler} instances with the desired properties.
 *
 * @author Jonatan Ivanov
 */
interface ExemplarSamplerFactory {

    /**
     * Creates an {@link ExemplarSampler} that stores the defined amount of exemplars.
     * @param numberOfExemplars the amount of exemplars stored by the sampler.
     * @return a new {@link ExemplarSampler} instance.
     */
    ExemplarSampler createExemplarSampler(int numberOfExemplars);

    /**
     * Creates an {@link ExemplarSampler} that stores exemplars for the defined histogram
     * buckets. This means as many exemplars as buckets are defined.
     * @param histogramUpperBounds histogram buckets to store exemplars for.
     * @return a new {@link ExemplarSampler} instance.
     */
    ExemplarSampler createExemplarSampler(double[] histogramUpperBounds);

}
