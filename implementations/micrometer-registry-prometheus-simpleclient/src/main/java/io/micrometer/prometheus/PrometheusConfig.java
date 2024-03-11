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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.Validated;

import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link PrometheusMeterRegistry}.
 *
 * @deprecated since 1.13.0, use the class with the same name from
 * io.micrometer:micrometer-registry-prometheus instead:
 * {@code io.micrometer.prometheusmetrics.PrometheusConfig}.
 * @author Jon Schneider
 */
@Deprecated
public interface PrometheusConfig extends MeterRegistryConfig {

    /**
     * Accept configuration defaults
     */
    PrometheusConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "prometheus";
    }

    /**
     * @return {@code true} if meter descriptions should be sent to Prometheus. Turn this
     * off to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        return getBoolean(this, "descriptions").orElse(true);
    }

    /**
     * @return The step size to use in computing windowed statistics like max. The default
     * is 1 minute. To get the most out of these statistics, align the step interval to be
     * close to your scrape interval.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    /**
     * Histogram type for backing DistributionSummary and Timer
     * @return Choose which type of histogram to use
     * @since 1.4.0
     */
    default HistogramFlavor histogramFlavor() {
        return getEnum(this, HistogramFlavor.class, "histogramFlavor").orElse(HistogramFlavor.Prometheus);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, checkRequired("step", PrometheusConfig::step),
                checkRequired("histogramFlavor", PrometheusConfig::histogramFlavor));
    }

}
