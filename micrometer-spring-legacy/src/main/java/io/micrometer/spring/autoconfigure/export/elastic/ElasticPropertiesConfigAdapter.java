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
package io.micrometer.spring.autoconfigure.export.elastic;

import io.micrometer.elastic.ElasticConfig;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.spring.autoconfigure.export.StepRegistryPropertiesConfigAdapter;
import io.micrometer.spring.autoconfigure.export.influx.InfluxProperties;

import java.util.concurrent.TimeUnit;

/**
 * Adapter to convert {@link ElasticProperties} to an {@link ElasticConfig}.
 *
 * @author Nicolas Portmann
 */
public class ElasticPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<ElasticProperties> implements ElasticConfig {

    public ElasticPropertiesConfigAdapter(ElasticProperties properties) {
        super(properties);
    }

    @Override
    public String[] hosts() {
        return get(ElasticProperties::getHosts, ElasticConfig.super::hosts);
    }

    @Override
    public String metricPrefix() {
        return get(ElasticProperties::getMetricPrefix, ElasticConfig.super::metricPrefix);
    }

    @Override
    public TimeUnit rateUnits() {
        return get(ElasticProperties::getRateUnits, ElasticConfig.super::rateUnits);
    }

    @Override
    public TimeUnit durationUnits() {
        return get(ElasticProperties::getDurationUnits, ElasticConfig.super::durationUnits);
    }

    @Override
    public int timeout() {
        return get(ElasticProperties::getTimeout, ElasticConfig.super::timeout);
    }

    @Override
    public String index() {
        return get(ElasticProperties::getIndex, ElasticConfig.super::index);
    }

    @Override
    public String indexDateFormat() {
        return get(ElasticProperties::getIndexDateFormat, ElasticConfig.super::indexDateFormat);

    }

    @Override
    public int bulkSize() {
        return get(ElasticProperties::getBulkSize, ElasticConfig.super::bulkSize);
    }

    @Override
    public String timeStampFieldName() {
        return get(ElasticProperties::getTimeStampFieldName, ElasticConfig.super::timeStampFieldName);
    }

    @Override
    public boolean autoCreateIndex() {
        return get(ElasticProperties::isAutoCreateIndex, ElasticConfig.super::autoCreateIndex);
    }

    @Override
    public String userName() {
        return get(ElasticProperties::getUserName, ElasticConfig.super::userName);
    }

    @Override
    public String password() {
        return get(ElasticProperties::getPassword, ElasticConfig.super::password);
    }
}
