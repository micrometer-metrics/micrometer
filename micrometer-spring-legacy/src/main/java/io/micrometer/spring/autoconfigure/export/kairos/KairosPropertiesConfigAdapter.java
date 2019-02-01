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
package io.micrometer.spring.autoconfigure.export.kairos;

import io.micrometer.kairos.KairosConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link KairosProperties} to a {@link KairosConfig}.
 *
 * @author Anton Ilinchik
 */
class KairosPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<KairosProperties> implements KairosConfig {

    KairosPropertiesConfigAdapter(KairosProperties properties) {
        super(properties);
    }

    @Override
    public String uri() {
        return get(KairosProperties::getUri, KairosConfig.super::uri);
    }

    @Override
    public String userName() {
        return get(KairosProperties::getUserName, KairosConfig.super::userName);
    }

    @Override
    public String password() {
        return get(KairosProperties::getPassword, KairosConfig.super::password);
    }
}

