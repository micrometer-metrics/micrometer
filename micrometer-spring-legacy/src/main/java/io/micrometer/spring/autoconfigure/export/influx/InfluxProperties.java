/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure.export.influx;

import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxConsistency;
import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Influx metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.influx")
public class InfluxProperties extends StepRegistryProperties implements InfluxConfig {

    @Override
    public String prefix() {
        return "spring.metrics.influx";
    }

    public void setDb(String db) {
        set("db", db);
    }

    public void setConsistency(InfluxConsistency consistency) {
        set("consistency", consistency);
    }

    public void setUserName(String userName) {
        set("userName", userName);
    }

    public void setPassword(String password) {
        set("password", password);
    }

    public void setRetentionPolicy(String retentionPolicy) {
        set("retentionPolicy", retentionPolicy);
    }

    public void setUri(String uri) {
        set("uri", uri);
    }

    public void setCompressed(Boolean compressed) {
        set("compressed", compressed);
    }

}
