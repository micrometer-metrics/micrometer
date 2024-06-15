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

import io.prometheus.metrics.config.ExemplarsProperties;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.core.exemplars.ExemplarSamplerConfig;
import io.prometheus.metrics.tracer.common.SpanContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of {@link ExemplarSamplerFactory}.
 *
 * @author Jonatan Ivanov
 */
class DefaultExemplarSamplerFactory implements ExemplarSamplerFactory {

    private final ExemplarsProperties exemplarsProperties;

    private final ConcurrentMap<Integer, ExemplarSamplerConfig> exemplarSamplerConfigsByNumberOfExemplars = new ConcurrentHashMap<>();

    private final ConcurrentMap<double[], ExemplarSamplerConfig> exemplarSamplerConfigsByHistogramUpperBounds = new ConcurrentHashMap<>();

    private final SpanContext spanContext;

    DefaultExemplarSamplerFactory(SpanContext spanContext, ExemplarsProperties exemplarsProperties) {
        this.spanContext = spanContext;
        this.exemplarsProperties = exemplarsProperties;
    }

    @Override
    public ExemplarSampler createExemplarSampler(int numberOfExemplars) {
        ExemplarSamplerConfig config = exemplarSamplerConfigsByNumberOfExemplars.computeIfAbsent(numberOfExemplars,
                key -> new ExemplarSamplerConfig(exemplarsProperties, numberOfExemplars));
        return new ExemplarSampler(config, spanContext);
    }

    @Override
    public ExemplarSampler createExemplarSampler(double[] histogramUpperBounds) {
        ExemplarSamplerConfig config = exemplarSamplerConfigsByHistogramUpperBounds.computeIfAbsent(
                histogramUpperBounds, key -> new ExemplarSamplerConfig(exemplarsProperties, histogramUpperBounds));
        return new ExemplarSampler(config, spanContext);
    }

}
