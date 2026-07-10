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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts known meter names from Micrometer's preferred name to Prometheus' preferred
 * name.
 *
 * @deprecated since 1.13.0, use the class with the same name from
 * io.micrometer:micrometer-registry-prometheus instead:
 * {@code io.micrometer.prometheusmetrics.PrometheusRenameFilter}.
 * @author Tommy Ludwig
 */
@Deprecated
public class PrometheusRenameFilter implements MeterFilter {

    private static final Map<String, String> MICROMETER_TO_PROMETHEUS_NAMES = new HashMap<>();

    static {
        MICROMETER_TO_PROMETHEUS_NAMES.put("process.files.open", "process.open.fds");
        MICROMETER_TO_PROMETHEUS_NAMES.put("process.files.max", "process.max.fds");
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        String convertedName = MICROMETER_TO_PROMETHEUS_NAMES.get(id.getName());
        return convertedName == null ? id : id.withName(convertedName);
    }

}
