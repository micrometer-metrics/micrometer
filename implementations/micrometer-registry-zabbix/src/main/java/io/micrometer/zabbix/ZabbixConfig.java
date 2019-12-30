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
package io.micrometer.zabbix;

import io.micrometer.core.instrument.push.PushRegistryConfig;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;

/**
 * Configuration for {@link ZabbixMeterRegistry}.
 */
public interface ZabbixConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "zabbix";
    }

    /**
     * @return The hostname of the Zabbix installation where the metrics will be shipped.
     */
    default String host() {
        return get(prefix() + ".host");
    }

    /**
     * @return The port of the Zabbix installation where the metrics will be shipped.
     */
    default int port() {
        String v = get(prefix() + ".port");
        return v == null ? 10051 : Integer.parseInt(v);
    }

    /**
     * @return The name of the host for the instance that is monitored.
     * @see <a href="https://www.zabbix.com/documentation/current/manual/config/hosts/host">Zabbix Host Configuration</a>
     */
    @Nullable
    default String hostTag() {
        String v = get(prefix() + ".hostTag");
        return v == null ? "instance" : v;
    }

    /**
     * @return Prefix that will be prepended on every key name.
     */
    @Nullable
    default String namePrefix() {
        return get(prefix() + ".namePrefix");
    }

    /**
     * @return Suffix that will be appended on every key name.
     */
    @Nullable
    default String nameSuffix() {
        return get(prefix() + ".nameSuffix");
    }

    /**
     * @return The delay between the publishing between to identical discovery items.
     * This value should be higher than {@link PushRegistryConfig#step()}. The default is 1 hour.
     */
    default Duration discoveryDelay() {
        String v = get(prefix() + ".discoveryDelay");
        return v == null ? Duration.ofHours(1) : Duration.parse(v);
    }
}
