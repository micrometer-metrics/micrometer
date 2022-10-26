/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.appdynamics.reporter;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.MetricPublisher;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AppDynamics Agent Reporter.
 *
 * @author Ricardo Veloso
 */
public class AppDynamicsAgentReporter implements AppDynamicsReporter {

    private final Logger logger = LoggerFactory.getLogger(AppDynamicsAgentReporter.class);

    private final MetricPublisher publisher;

    private final NamingConvention convention;

    public AppDynamicsAgentReporter(NamingConvention namingConvention) {
        this(AppdynamicsAgent.getMetricPublisher(), namingConvention);
    }

    public AppDynamicsAgentReporter(MetricPublisher publisher, NamingConvention convention) {
        this.publisher = publisher;
        this.convention = convention;
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(convention);
    }

    @Override
    public void publishSumValue(Meter.Id id, long value) {
        logger.debug("publishing sum {}", id.getName());
        publisher.reportMetric(getConventionName(id), value, "SUM", "SUM", "COLLECTIVE");
    }

    @Override
    public void publishObservation(Meter.Id id, long value) {
        logger.debug("publishing observation {}", id.getName());
        publisher.reportMetric(getConventionName(id), value, "OBSERVATION", "CURRENT", "COLLECTIVE");
    }

    @Override
    public void publishAggregation(Meter.Id id, long count, long value, long min, long max) {
        if (count > 0) {
            logger.debug("publishing aggregation {}", id.getName());
            publisher.reportMetric(getConventionName(id), value, count, min, max, "AVERAGE", "AVERAGE", "INDIVIDUAL");
        }
        else {
            logger.debug("ignore aggregation with no observation {}", id.getName());
        }
    }

}
