/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.boot2.samples.components;

import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.health.HealthConfig;
import io.micrometer.health.HealthMeterRegistry;
import io.micrometer.health.ServiceLevelObjective;
import io.micrometer.health.objectives.JvmServiceLevelObjectives;
import io.micrometer.health.objectives.OperatingSystemServiceLevelObjectives;
import org.springframework.boot.actuate.health.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delete this when this feature is incorporated into Spring Boot.
 *
 * @author Jon Schneider
 */
@Configuration
public class ServiceLevelObjectiveConfiguration {

    private final GenericApplicationContext applicationContext;

    private final NamingConvention camelCasedHealthIndicatorNames = NamingConvention.camelCase;

    public ServiceLevelObjectiveConfiguration(GenericApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    HealthMeterRegistry healthMeterRegistry() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .serviceLevelObjectives(JvmServiceLevelObjectives.MEMORY)
            .serviceLevelObjectives(OperatingSystemServiceLevelObjectives.DISK)
            .serviceLevelObjectives(ServiceLevelObjective.build("api.error.ratio")
                .failedMessage("API error ratio")
                .baseUnit(BaseUnits.PERCENT)
                .tag("uri.matches", "/api/**")
                .tag("error.outcome", "SERVER_ERROR")
                .errorRatio(s -> s.name("http.server.requests").tag("uri", uri -> uri.startsWith("/api")),
                        all -> all.tag("outcome", "SERVER_ERROR"))
                .isLessThan(0.01))
            .build();

        for (ServiceLevelObjective slo : registry.getServiceLevelObjectives()) {
            applicationContext.registerBean(camelCasedHealthIndicatorNames.name(slo.getName(), Type.GAUGE),
                    HealthContributor.class, () -> toHealthContributor(registry, slo));
        }

        return registry;
    }

    private HealthContributor toHealthContributor(HealthMeterRegistry registry, ServiceLevelObjective slo) {
        if (slo instanceof ServiceLevelObjective.SingleIndicator) {
            return new AbstractHealthIndicator(slo.getFailedMessage()) {
                @Override
                protected void doHealthCheck(Health.Builder builder) {
                    ServiceLevelObjective.SingleIndicator singleIndicator = (ServiceLevelObjective.SingleIndicator) slo;
                    builder.status(slo.healthy(registry) ? Status.UP : Status.OUT_OF_SERVICE)
                        .withDetail("value", singleIndicator.getValueAsString(registry))
                        .withDetail("mustBe", singleIndicator.getTestDescription());

                    for (Tag tag : slo.getTags()) {
                        builder.withDetail(camelCasedHealthIndicatorNames.tagKey(tag.getKey()), tag.getValue());
                    }

                    if (slo.getBaseUnit() != null) {
                        builder.withDetail("unit", slo.getBaseUnit());
                    }
                }
            };
        }
        else {
            ServiceLevelObjective.MultipleIndicator multipleIndicator = (ServiceLevelObjective.MultipleIndicator) slo;
            Map<String, HealthContributor> objectiveIndicators = Arrays.stream(multipleIndicator.getObjectives())
                .collect(Collectors.toMap(
                        indicator -> camelCasedHealthIndicatorNames.name(indicator.getName(), Type.GAUGE),
                        indicator -> toHealthContributor(registry, indicator)));
            return CompositeHealthContributor.fromMap(objectiveIndicators);
        }
    }

}
