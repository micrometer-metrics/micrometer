/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts known metric names from Micrometer's preferred name to Prometheus' preferred name.
 *
 * @author Tommy Ludwig
 */
public class PrometheusMetricRenameFilter implements MeterFilter {

    private static final Map<String, String> MICROMETER_TO_PROMETHEUS_METRIC_NAMES = new HashMap<>();

    static {
        MICROMETER_TO_PROMETHEUS_METRIC_NAMES.put("process.fds.open", "process.open.fds");
        MICROMETER_TO_PROMETHEUS_METRIC_NAMES.put("process.fds.max", "process.max.fds");
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        String convertedMetricName = MICROMETER_TO_PROMETHEUS_METRIC_NAMES.get(id.getName());
        return convertedMetricName == null ? id :
            new Meter.Id(convertedMetricName, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
    }
}
