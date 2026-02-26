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
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;

class OtlpExemplarSamplerFactory {

    private final ExemplarContextProvider exemplarContextProvider;

    private final Clock clock;

    private final OtlpConfig config;

    private final TimeUnit baseTimeUnit;

    OtlpExemplarSamplerFactory(ExemplarContextProvider exemplarContextProvider, Clock clock, OtlpConfig config) {
        this.exemplarContextProvider = exemplarContextProvider;
        this.clock = clock;
        this.config = config;
        this.baseTimeUnit = config.baseTimeUnit();
    }

    ExemplarSampler create(int size, boolean timeBased) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, size, converter(timeBased));
    }

    ExemplarSampler create(double[] buckets, boolean timeBased) {
        return new OtlpExemplarSampler(exemplarContextProvider, clock, config, buckets, converter(timeBased));
    }

    private DoubleUnaryOperator converter(boolean timeBased) {
        return timeBased ? this::nanosToBaseTimeUnit : DoubleUnaryOperator.identity();
    }

    private double nanosToBaseTimeUnit(double value) {
        return TimeUtils.nanosToUnit(value, baseTimeUnit);
    }

}
