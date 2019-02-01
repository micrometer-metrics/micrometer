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
package io.micrometer.spring.autoconfigure.export.signalfx;

import io.micrometer.signalfx.SignalFxConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link SignalFxProperties} to a {@link SignalFxConfig}.
 *
 * @author Jon Schneider
 */
class SignalFxPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<SignalFxProperties>
        implements SignalFxConfig {

    SignalFxPropertiesConfigAdapter(SignalFxProperties properties) {
        super(properties);
        accessToken(); // validate that an access token is set
    }

    @Override
    public String accessToken() {
        return get(SignalFxProperties::getAccessToken, SignalFxConfig.super::accessToken);
    }

    @Override
    public String uri() {
        return get(SignalFxProperties::getUri, SignalFxConfig.super::uri);
    }

    @Override
    public String source() {
        return get(SignalFxProperties::getSource, SignalFxConfig.super::source);
    }
}
