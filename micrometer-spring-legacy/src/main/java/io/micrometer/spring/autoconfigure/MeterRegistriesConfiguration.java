/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure;

import io.micrometer.spring.autoconfigure.export.atlas.AtlasExportConfiguration;
import io.micrometer.spring.autoconfigure.export.datadog.DatadogExportConfiguration;
import io.micrometer.spring.autoconfigure.export.ganglia.GangliaExportConfiguration;
import io.micrometer.spring.autoconfigure.export.graphite.GraphiteExportConfiguration;
import io.micrometer.spring.autoconfigure.export.influx.InfluxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.jmx.JmxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.newrelic.NewRelicExportConfiguration;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusExportConfiguration;
import io.micrometer.spring.autoconfigure.export.signalfx.SignalFxExportConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleExportConfiguration;
import io.micrometer.spring.autoconfigure.export.statsd.StatsdExportConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Jon Schneider
 */
@Configuration
@Import({
    AtlasExportConfiguration.class, DatadogExportConfiguration.class,
    GangliaExportConfiguration.class, GraphiteExportConfiguration.class,
    InfluxExportConfiguration.class, NewRelicExportConfiguration.class,
    JmxExportConfiguration.class, StatsdExportConfiguration.class,
    PrometheusExportConfiguration.class, SimpleExportConfiguration.class,
    SignalFxExportConfiguration.class,
})
public class MeterRegistriesConfiguration {
}
