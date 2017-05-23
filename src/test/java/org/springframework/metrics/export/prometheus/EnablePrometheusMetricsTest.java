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
package org.springframework.metrics.export.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class EnablePrometheusMetricsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void tagFormatting() {
        assertThat(context.getBean(TagFormatter.class))
                .isInstanceOf(PrometheusTagFormatter.class);
    }

    @Test
    void meterRegistry() {
        assertThat(context.getBean(MeterRegistry.class))
                .isInstanceOf(PrometheusMeterRegistry.class);
    }

    @SpringBootApplication
    @EnablePrometheusMetrics
    static class PrometheusApp {}
}
