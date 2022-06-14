/**
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.observation.transport.http.tags.convention;

import io.micrometer.common.docs.KeyName;
import io.micrometer.conventions.semantic.SemanticAttributes;

/**
 * Conventions for HTTP client key names implemented with OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum OpenTelemetryHttpClientLowCardinalityKeyNames implements KeyName {
    /**
     * Remote hostname or similar, see note below.
     *
     * Examples: example.com
     *
     * SHOULD NOT be set if capturing it would require an extra DNS lookup.
     * @param request
     * @return
     */
    PEER_NAME {
        @Override
        public String getKeyName() {
            return SemanticAttributes.NET_PEER_NAME.getKey();
        }
    }
}
