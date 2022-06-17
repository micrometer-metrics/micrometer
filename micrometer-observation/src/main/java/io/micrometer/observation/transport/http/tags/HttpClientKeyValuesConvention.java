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
package io.micrometer.observation.transport.http.tags;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;

/**
 * Conventions for HTTP client key values.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface HttpClientKeyValuesConvention extends HttpKeyValuesConvention {

    /**
     * A no-op instance.
     */
    HttpClientKeyValuesConvention NOOP = new HttpClientKeyValuesConvention() {
        @Override
        public KeyValue peerName(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue method(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue url(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue target(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue host(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue scheme(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue statusCode(HttpResponse response) {
            return null;
        }

        @Override
        public KeyValue flavor(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue userAgent(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue requestContentLength(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue responseContentLength(HttpResponse response) {
            return null;
        }

        @Override
        public KeyValue ip(HttpRequest request) {
            return null;
        }

        @Override
        public KeyValue port(HttpRequest request) {
            return null;
        }
    };

    /**
     * Remote hostname or similar, see note below.
     *
     * Examples: example.com
     *
     * SHOULD NOT be set if capturing it would require an extra DNS lookup.
     * @param request HTTP request
     * @return key value
     */
    KeyValue peerName(HttpRequest request);

    @Override
    default KeyValues all(HttpRequest request, HttpResponse response) {
        return HttpKeyValuesConvention.super.all(request, response).and(peerName(request));
    }

}
