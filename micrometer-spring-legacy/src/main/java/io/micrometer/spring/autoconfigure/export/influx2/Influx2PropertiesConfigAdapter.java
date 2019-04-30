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
package io.micrometer.spring.autoconfigure.export.influx2;

import io.micrometer.influx2.Influx2Config;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link Influx2Properties} to an {@link InfluxConfig}.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
class Influx2PropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<Influx2Properties> implements Influx2Config {

    Influx2PropertiesConfigAdapter(Influx2Properties properties) {
        super(properties);
    }

    @Override
    public String bucket() {
        return get(Influx2Properties::getBucket, Influx2Config.super::bucket);
    }

    @Override
    public String org() {
        return get(Influx2Properties::getOrg, Influx2Config.super::org);
    }

    @Override
    public String token() {
        return get(Influx2Properties::getToken, Influx2Config.super::token);
    }

    @Override
    public String uri() {
        return get(Influx2Properties::getUri, Influx2Config.super::uri);
    }

    @Override
    public boolean compressed() {
        return get(Influx2Properties::isCompressed, Influx2Config.super::compressed);
    }

    @Override
    public boolean autoCreateBucket() {
        return get(Influx2Properties::isAutoCreateBucket, Influx2Config.super::autoCreateBucket);
    }

    @Override
    public Integer everySeconds() {
        return get(Influx2Properties::getEverySeconds, Influx2Config.super::everySeconds);
    }
}
