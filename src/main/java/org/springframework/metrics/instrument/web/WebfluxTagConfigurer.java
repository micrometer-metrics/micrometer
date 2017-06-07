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

import org.springframework.http.HttpStatus;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Arrays.asList;

public class WebfluxTagConfigurer {
    private final TagFormatter tagFormatter;

    public WebfluxTagConfigurer(TagFormatter tagFormatter) {
        this.tagFormatter = tagFormatter;
    }

    /**
     * Supplies default tags to the WebFlux annotation-based server programming model.
     */
    Iterable<Tag> httpRequestTags(ServerWebExchange exchange, Throwable exception) {
        return asList(method(exchange), uri(exchange), exception(exception), status(exchange));
    }

    public Tag uri(ServerWebExchange exchange) {
        String rawUri = (String) exchange.getAttribute(org.springframework.web.reactive.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).orElse(null);

        if (!StringUtils.hasText(rawUri)) {
            rawUri = "/";
        }
        rawUri = tagFormatter.formatTagValue(rawUri.substring(1));
        return Tag.of("uri", rawUri.isEmpty() ? "root" : rawUri);
    }

    public Tag method(ServerWebExchange exchange) {
        return Tag.of("method", exchange.getRequest().getMethod().toString());
    }

    public Tag status(ServerWebExchange exchange) {
        HttpStatus status = exchange.getResponse().getStatusCode();
        if(status == null)
            status = HttpStatus.OK;
        return Tag.of("status", status.toString());
    }

    public Tag exception(Throwable exception) {
        if (exception != null) {
            return Tag.of("exception", exception.getClass().getSimpleName());
        }
        return Tag.of("exception", "None");
    }
}
