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
package io.micrometer.spring.autoconfigure.export.humio;

import io.micrometer.humio.HumioConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

import java.util.Map;

/**
 * Adapter to convert {@link HumioProperties} to a {@link HumioConfig}.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
class HumioPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<HumioProperties> implements HumioConfig {

    HumioPropertiesConfigAdapter(HumioProperties properties) {
        super(properties);
    }

    @Override
    public String uri() {
        return get(HumioProperties::getUri, HumioConfig.super::uri);
    }

    @Override
    public String apiToken() {
        return get(HumioProperties::getApiToken, HumioConfig.super::apiToken);
    }

    @Override
    public String repository() {
        return get(HumioProperties::getRepository, HumioConfig.super::repository);
    }

    @Override
    public Map<String, String> tags() {
        return get(HumioProperties::getTags, HumioConfig.super::tags);
    }
}
