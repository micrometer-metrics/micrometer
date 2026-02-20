/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;

// TODO: should not be public but OtlpCumulativeBucketHistogram is public with a protected ctor
public class OtlpExemplarSamplerFactory {

    private final ExemplarContextProvider exemplarContextProvider;

    private final Clock clock;

    private final OtlpConfig config;

    OtlpExemplarSamplerFactory(ExemplarContextProvider exemplarContextProvider, Clock clock, OtlpConfig config) {
        this.exemplarContextProvider = exemplarContextProvider;
        this.clock = clock;
        this.config = config;
    }

    ExemplarSampler createExemplarSampler(int numberOfExemplars) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, numberOfExemplars);
    }

    ExemplarSampler createExemplarSampler(double[] buckets) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, buckets);
    }

    ExemplarSampler createTimeBasedExemplarSampler(int numberOfExemplars) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, numberOfExemplars,
                config.baseTimeUnit());
    }

    ExemplarSampler createTimeBasedExemplarSampler(double[] buckets) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, buckets, config.baseTimeUnit());
    }

}
