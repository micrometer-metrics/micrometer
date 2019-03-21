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
package io.micrometer.spring.autoconfigure.export.appoptics;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link AppOpticsProperties} to an {@link AppOpticsConfig}.
 *
 * @author Hunter Sherman
 * @author Stephane Nicoll
 */
class AppOpticsPropertiesConfigAdapter
        extends StepRegistryPropertiesConfigAdapter<AppOpticsProperties>
        implements AppOpticsConfig {

    AppOpticsPropertiesConfigAdapter(AppOpticsProperties properties) {
        super(properties);
    }

    @Override
    public String uri() {
        return get(AppOpticsProperties::getUri, AppOpticsConfig.super::uri);
    }

    @Override
    public String apiToken() {
        return get(AppOpticsProperties::getApiToken, AppOpticsConfig.super::apiToken);
    }

    @Override
    public String hostTag() {
        return get(AppOpticsProperties::getHostTag, AppOpticsConfig.super::hostTag);
    }

}
