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
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;

/**
 * Conventions for HTTP key values.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
// TODO: Shouldn't we have a default implementation?
public interface HttpKeyValuesConvention extends Observation.KeyValuesConvention {

    /**
     * HTTP request method.
     *
     * Examples:
     * GET; POST; HEAD
     *
     * @param request
     * @return
     */
    KeyValue method(HttpRequest request);

    /**
     * Full HTTP request URL in the form scheme://host[:port]/path?query[#fragment]. Usually the fragment is not transmitted over HTTP, but if it is known, it should be included nevertheless.
     *
     * Examples:
     * https://www.foo.bar/search?q=OpenTelemetry#SemConv
     *
     * @param request
     * @return
     */
    KeyValue url(HttpRequest request);

    /**
     * The full request target as passed in a HTTP request line or equivalent.
     *
     * Examples:
     * /path/12314/?q=ddds#123
     *
     * @param request
     * @return
     */
    KeyValue target(HttpRequest request);

    /**
     * The value of the HTTP host header. An empty Host header should also be reported, see note.
     *
     * Examples:
     * www.example.org
     *
     * @param request
     * @return
     */
    KeyValue host(HttpRequest request);

    /**
     * The URI scheme identifying the used protocol.
     *
     * Examples:
     * http; https
     *
     * @param request
     * @return
     */
    KeyValue scheme(HttpRequest request);

    /**
     * HTTP response status code.
     *
     * Examples:
     * 200
     *
     * @param response
     * @return
     */
    KeyValue statusCode(HttpResponse response);

    /**
     * Kind of HTTP protocol used.
     *
     * Examples:
     * 1.0
     *
     * @param request
     * @return
     */
    KeyValue flavor(HttpRequest request);

    /**
     * Value of the HTTP User-Agent header sent by the client.
     *
     * Examples:
     * CERN-LineMode/2.15 libwww/2.17b3
     *
     * @param request
     * @return
     */
    KeyValue userAgent(HttpRequest request);

    /**
     * The size of the request payload body in bytes. This is the number of bytes transferred excluding headers and is often, but not always, present as the Content-Length header. For requests using transport encoding, this should be the compressed size.
     *
     * Examples:
     * 3495
     *
     * @param request
     * @return
     */
    KeyValue requestContentLength(HttpRequest request);

    /**
     * The size of the response payload body in bytes. This is the number of bytes transferred excluding headers and is often, but not always, present as the Content-Length header. For requests using transport encoding, this should be the compressed size.
     *
     * Examples:
     * 3495
     *
     * @param response
     * @return
     */
    KeyValue responseContentLength(HttpResponse response);

    /**
     * Remote address of the peer (dotted decimal for IPv4 or RFC5952 for IPv6)
     *
     * Examples:
     * 127.0.0.1
     *
     * @param request
     * @return
     */
    KeyValue ip(HttpRequest request);

    /**
     * Remote port number.
     *
     * Examples:
     * 80; 8080; 443
     *
     * @param request
     * @return
     */
    KeyValue port(HttpRequest request);

    /**
     * Sets all key values.
     *
     * @param request
     * @param response
     * @return
     */
    default KeyValues all(HttpRequest request, HttpResponse response) {
        return KeyValues.of(method(request), url(request), target(request), host(request), scheme(request), statusCode(response), flavor(request), userAgent(request), requestContentLength(request), responseContentLength(response), ip(request), port(request));
    }
}
