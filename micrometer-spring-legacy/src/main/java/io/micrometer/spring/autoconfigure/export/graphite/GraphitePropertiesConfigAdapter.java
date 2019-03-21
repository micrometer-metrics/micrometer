/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure.export.graphite;

import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteProtocol;
import io.micrometer.spring.autoconfigure.export.properties.PropertiesConfigAdapter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Adapter to convert {@link GraphiteProperties} to a {@link GraphiteConfig}.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
class GraphitePropertiesConfigAdapter extends PropertiesConfigAdapter<GraphiteProperties>
    implements GraphiteConfig {

    GraphitePropertiesConfigAdapter(GraphiteProperties properties) {
        super(properties);
    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public boolean enabled() {
        return get(GraphiteProperties::isEnabled, GraphiteConfig.super::enabled);
    }

    @Override
    public Duration step() {
        return get(GraphiteProperties::getStep, GraphiteConfig.super::step);
    }

    @Override
    public TimeUnit rateUnits() {
        return get(GraphiteProperties::getRateUnits, GraphiteConfig.super::rateUnits);
    }

    @Override
    public TimeUnit durationUnits() {
        return get(GraphiteProperties::getDurationUnits,
            GraphiteConfig.super::durationUnits);
    }

    @Override
    public String host() {
        return get(GraphiteProperties::getHost, GraphiteConfig.super::host);
    }

    @Override
    public int port() {
        return get(GraphiteProperties::getPort, GraphiteConfig.super::port);
    }

    @Override
    public GraphiteProtocol protocol() {
        return get(GraphiteProperties::getProtocol, GraphiteConfig.super::protocol);
    }

    @Override
    public String[] tagsAsPrefix() {
        return get(GraphiteProperties::getTagsAsPrefix, GraphiteConfig.super::tagsAsPrefix);
    }
}
