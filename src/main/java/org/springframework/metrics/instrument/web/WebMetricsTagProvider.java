/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.web;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.metrics.instrument.Tag;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.stream.Stream;

/**
 * Defines the default set of tags added to instrumented web requests. It is only necessary to implement
 * providers for the programming model(s) you are using.
 *
 * @author Jon Schneider
 */
public interface WebMetricsTagProvider {
    /**
     * Supplies default tags to timers monitoring RestTemplate requests.
     *
     * @param request  RestTemplate client HTTP request
     * @param response may be null in the event of a client error
     * @return a map of tags added to every client HTTP request metric
     */
    default Stream<Tag> clientHttpRequestTags(HttpRequest request,
                                      ClientHttpResponse response) {
        return Stream.empty();
    }

    /**
     * Supplies default tags to long task timers monitoring the Web MVC server programming model.
     *
     * @param request  HTTP request
     * @param handler  the request method that is responsible for handling the request
     * @return a map of tags added to every Spring MVC HTTP request metric
     */
    default Stream<Tag> httpLongRequestTags(HttpServletRequest request, Object handler) {
        return Stream.empty();
    }

    /**
     * Supplies default tags to the Web MVC server programming model.
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @param handler  the request method that is responsible for handling the request
     * @return a map of tags added to every Spring MVC HTTP request metric
     */
    default Stream<Tag> httpRequestTags(HttpServletRequest request,
                                HttpServletResponse response, Object handler) {
        return Stream.empty();
    }

    /**
     * Supplies default tags to the WebFlux annotation-based server programming model.
     * @param exchange
     * @param exception
     * @return
     */
    default Stream<Tag> httpRequestTags(ServerWebExchange exchange, Throwable exception) {
        return Stream.empty();
    }

    /**
     * Supplies default tags to the WebFlux functional server programming model.
     * @param request
     * @param response
     * @param uri
     * @param exception
     * @return
     */
    default Stream<Tag> httpRequestTags(ServerRequest request, ServerResponse response, String uri, Throwable exception) {
        return Stream.empty();
    }
}
