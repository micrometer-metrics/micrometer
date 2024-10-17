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
package io.micrometer.prometheusmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;

/**
 * See <a href=
 * "https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels">Prometheus
 * docs</a> for a specification of the constraints on metric names and labels
 *
 * @author Jon Schneider
 * @since 1.13.0
 */
public class PrometheusNamingConvention implements NamingConvention {

    private final String timerSuffix;

    public PrometheusNamingConvention() {
        this("");
    }

    public PrometheusNamingConvention(String timerSuffix) {
        this.timerSuffix = timerSuffix;
    }

    /**
     * Names are snake-cased. They contain a base unit suffix when applicable.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String conventionName = NamingConvention.snakeCase.name(name, type, baseUnit);

        switch (type) {
            case COUNTER:
            case DISTRIBUTION_SUMMARY:
            case GAUGE:
                if (baseUnit != null && !conventionName.endsWith("_" + baseUnit))
                    conventionName += "_" + baseUnit;
                break;
        }

        switch (type) {
            // The earlier version of this logic in prometheus-simpleclient
            // handled the case of COUNTER and appended "_total" if conventionName did not
            // end with it. With the new client, we mustn't do this since it appends
            // "_total" on its own and also validates and fails if it is already there:
            // java.lang.IllegalArgumentException: 'api_requests_total': Illegal metric
            // name. The metric name must not include the '_total' suffix. Call
            // PrometheusNaming.sanitizeMetricName(name) to avoid this error.
            case TIMER:
            case LONG_TASK_TIMER:
                if (!timerSuffix.isEmpty() && conventionName.endsWith(timerSuffix)) {
                    conventionName += "_seconds";
                }
                else if (!conventionName.endsWith("_seconds")) {
                    conventionName += timerSuffix + "_seconds";
                }
                break;
        }

        return PrometheusNaming.sanitizeMetricName(conventionName);
    }

    /**
     * Label names may contain ASCII letters, numbers, as well as underscores. They must
     * match the regex [a-zA-Z_][a-zA-Z0-9_]*. Label names beginning with __ are reserved
     * for internal use.
     */
    @Override
    public String tagKey(String key) {
        return PrometheusNaming.sanitizeLabelName(key);
    }

}
