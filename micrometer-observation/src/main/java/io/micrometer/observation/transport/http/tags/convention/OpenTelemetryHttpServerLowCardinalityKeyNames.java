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
 * Conventions for HTTP server key names implemented with OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum OpenTelemetryHttpServerLowCardinalityKeyNames implements KeyName {
    /**
     * The primary server name of the matched virtual host. This should be obtained via
     * configuration. If no such configuration can be obtained, this attribute MUST NOT be
     * set ( net.host.name should be used instead).
     *
     * Examples: example.com
     * @param request
     * @return
     */
    SERVER_NAME {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_SERVER_NAME.getKey();
        }
    },

    // TODO: In OTEL - we will set not templated version
    /**
     * The matched route.
     *
     * Examples: /users/5
     * @param request
     * @return
     */
    ROUTE {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_ROUTE.getKey();
        }
    },

    // TODO: Not in OTEL
    /**
     * The matched route (path template).
     *
     * Examples: /users/:userID?
     * @param request
     * @return
     */
    TEMPLATED_ROUTE {
        @Override
        public String getKeyName() {
            return "http.route.templated";
        }
    },

    /**
     * The IP address of the original client behind all proxies, if known (e.g. from
     * X-Forwarded-For).
     *
     * Examples: 83.164.160.102
     * @param request
     * @return
     */
    CLIENT_IP {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_CLIENT_IP.getKey();
        }
    },
}
