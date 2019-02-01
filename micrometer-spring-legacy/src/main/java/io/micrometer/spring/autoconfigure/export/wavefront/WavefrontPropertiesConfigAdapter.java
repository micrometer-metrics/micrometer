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
package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;
import io.micrometer.wavefront.WavefrontConfig;

/**
 * Adapter to convert {@link WavefrontProperties} to a {@link WavefrontConfig}.
 *
 * @author Jon Schneider
 */
class WavefrontPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<WavefrontProperties> implements WavefrontConfig {

    WavefrontPropertiesConfigAdapter(WavefrontProperties properties) {
        super(properties);
    }

    @Override
    public String get(String k) {
        return null;
    }

    @Override
    public String uri() {
        return get(this::getUriAsString, WavefrontConfig.DEFAULT_DIRECT::uri);
    }

    @Override
    public String source() {
        return get(WavefrontProperties::getSource, WavefrontConfig.super::source);
    }

    @Override
    public String apiToken() {
        return get(WavefrontProperties::getApiToken, WavefrontConfig.super::apiToken);
    }

    @Override
    public String globalPrefix() {
        return get(WavefrontProperties::getGlobalPrefix, WavefrontConfig.super::globalPrefix);
    }

    private String getUriAsString(WavefrontProperties properties) {
        return (properties.getUri() != null) ? properties.getUri().toString() : null;
    }

}
