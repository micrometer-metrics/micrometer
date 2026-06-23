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
package io.micrometer.prometheusmetrics;

import io.prometheus.metrics.config.ExemplarsProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultExemplarSamplerFactoryTest {

    @Test
    void exemplarSamplerConfigsByHistogramUpperBoundsCacheIsEffective() {
        ExemplarsProperties properties = PrometheusPropertiesLoader.load().getExemplarProperties();
        DefaultExemplarSamplerFactory factory = new DefaultExemplarSamplerFactory(mock(SpanContext.class), properties);

        double[] bounds1 = new double[] { 1.0, 2.0, 5.0, Double.POSITIVE_INFINITY };
        double[] bounds2 = new double[] { 1.0, 2.0, 5.0, Double.POSITIVE_INFINITY };

        // Create samplers with two different array instances having the same values
        factory.createExemplarSampler(bounds1);
        factory.createExemplarSampler(bounds2);

        // If the cache is effective, there should only be 1 entry in the map
        assertThat(factory.exemplarSamplerConfigsByHistogramUpperBounds).hasSize(1);
    }

}
