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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Conventions for HTTP server key values.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface HttpServerKeyValuesConvention<REQ, RES> extends HttpKeyValuesConvention<REQ, RES> {

    /**
     * The primary server name of the matched virtual host. This should be obtained via
     * configuration. If no such configuration can be obtained, this attribute MUST NOT be
     * set ( net.host.name should be used instead).
     * <p>
     * Examples: example.com
     * @param request HTTP request
     * @return key value
     */
    KeyValue serverName(REQ request);

    // TODO: In OTel - we will set not templated version

    /**
     * The matched route.
     * <p>
     * Examples: /users/5
     * @param request HTTP request
     * @return key value
     */
    KeyValue route(REQ request);

    // TODO: Not in OTEL

    /**
     * The matched route (path template).
     * <p>
     * Examples: /users/:userID?
     * @param request HTTP request
     * @return key value
     */
    KeyValue templatedRoute(REQ request);

    /**
     * The IP address of the original client behind all proxies, if known (e.g. from
     * X-Forwarded-For).
     * <p>
     * Examples: 83.164.160.102
     * @param request HTTP request
     * @return key value
     */
    KeyValue clientIp(REQ request);

    @Override
    default KeyValues all(REQ request, RES response) {
        return HttpKeyValuesConvention.super.all(request, response).and(serverName(request), route(request),
                templatedRoute(request), clientIp(request));
    }

}
