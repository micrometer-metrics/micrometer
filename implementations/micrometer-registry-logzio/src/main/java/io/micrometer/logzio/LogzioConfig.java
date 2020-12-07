/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.logzio;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.HashMap;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Configuration for {@link LogzioMeterRegistry}.
 *
 * @author
 * @since
 */
public interface LogzioConfig extends StepRegistryConfig {

    default HashMap<String, String> regionsUri(){
        return new HashMap<String, String>(){{
            put("us", "http://listener.logz.io:8070");
            put("ca", "http://listener-ca.logz.io:8070");
            put("eu", "http://listener-eu.logz.io:8070");
            put("nl", "http://listener-nl.logz.io:8070");
            put("uk", "http://listener-uk.logz.io:8070");
            put("wa", "http://listener-wa.logz.io:8070");
        }};
    }
    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "logzio";
    }

    default String uri() {
        String region = region();
        if(region != null){
            HashMap<String, String> regionsToUri = regionsUri();
            if( regionsToUri.containsKey(region)) {
                return regionsToUri.get(region);
            }
        }
        return getUrlString(this, "uri").orElse("http://listener.logz.io:8070");
    }

    default String region() {
        return getString(this, "region").orElse("null");
    }

    default String token() {
        return getString(this, "token").required().get();
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("token", LogzioConfig::token),
                checkRequired("uri", LogzioConfig::uri)
        );
    }
}
