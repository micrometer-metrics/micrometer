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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * Adds a sensible set of tags to Spring Web/Webflux timers and RestTemplate/WebClient timers. Note that
 * HTTP status is added by default to Spring Web/Webflux timers, requiring Servlet 3.0+ (i.e. Tomcat 7+).
 *
 * @author Jon Schneider
 */
public class DefaultWebMetricsTagProvider implements WebMetricsTagProvider {
    @Override
    public Stream<Tag> clientHttpRequestTags(HttpRequest request,
                                             ClientHttpResponse response) {
        String urlTemplate = RestTemplateUrlTemplateHolder.getRestTemplateUrlTemplate();
        if (urlTemplate == null) {
            urlTemplate = "none";
        }

        String status;
        try {
            status = (response == null) ? "CLIENT_ERROR" : ((Integer) response
                    .getRawStatusCode()).toString();
        } catch (IOException e) {
            status = "IO_ERROR";
        }

        String host = request.getURI().getHost();
        if (host == null) {
            host = "none";
        }

        String strippedUrlTemplate = urlTemplate.replaceAll("^https?://[^/]+/", "");

        return Stream.of(Tag.of("method", request.getMethod().name()),
                Tag.of("uri", sanitizeUrlTemplate(strippedUrlTemplate)),
                Tag.of("status", status),
                Tag.of("clientName", host));
    }

    @Override
    public Stream<Tag> httpLongRequestTags(HttpServletRequest request, Object handler) {
        Stream.Builder<Tag> tags = Stream.builder();

        tags.add(Tag.of("method", request.getMethod()));

        String uri = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (uri == null) {
            uri = request.getPathInfo();
        }
        if (!StringUtils.hasText(uri)) {
            uri = "/";
        }
        uri = sanitizeUrlTemplate(uri.substring(1));
        tags.add(Tag.of("uri", uri.isEmpty() ? "root" : uri));

        return tags.build();
    }

    @Override
    public Stream<Tag> httpRequestTags(HttpServletRequest request,
                                       HttpServletResponse response, Object handler) {
        Stream.Builder<Tag> tags = Stream.builder();

        tags.add(Tag.of("method", request.getMethod()));
        tags.add(Tag.of("status", ((Integer) response.getStatus()).toString()));

        String uri = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (uri == null) {
            uri = request.getPathInfo();
        }
        if (!StringUtils.hasText(uri)) {
            uri = "/";
        }
        uri = sanitizeUrlTemplate(uri.substring(1));
        tags.add(Tag.of("uri", uri.isEmpty() ? "root" : uri));

        Object exception = request.getAttribute("exception");
        if (exception != null) {
            tags.add(Tag.of("exception", exception.getClass().getSimpleName()));
        }

        return tags.build();
    }

    @Override
    public Stream<Tag> httpRequestTags(ServerWebExchange exchange, Throwable exception) {
        Stream.Builder<Tag> tags = Stream.builder();

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        tags.add(Tag.of("method", request.getMethod().toString()));

        // FIXME determined too late
//        tags.add(Tag.of("status", response.getStatusCode().toString()));

        String uri = (String) exchange.getAttribute(org.springframework.web.reactive.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).orElse(null);
        if (!StringUtils.hasText(uri)) {
            uri = "/";
        }
        uri = sanitizeUrlTemplate(uri.substring(1));
        tags.add(Tag.of("uri", uri.isEmpty() ? "root" : uri));

        if (exception != null) {
            tags.add(Tag.of("exception", exception.getClass().getSimpleName()));
        }

        return tags.build();
    }

    @Override
    public Stream<Tag> httpRequestTags(ServerRequest request, ServerResponse response, String uri, Throwable exception) {
        Stream.Builder<Tag> tags = Stream.builder();

        tags.add(Tag.of("method", request.method().toString()));
        tags.add(Tag.of("status", response.statusCode().toString()));

        if (!StringUtils.hasText(uri)) {
            uri = "/";
        }
        uri = sanitizeUrlTemplate(uri.substring(1));
        tags.add(Tag.of("uri", uri.isEmpty() ? "root" : uri));

        if (exception != null) {
            tags.add(Tag.of("exception", exception.getClass().getSimpleName()));
        }

        return tags.build();
    }

    /**
     * As is, the urlTemplate is not suitable for use with Atlas, as all interactions with
     * Atlas take place via query parameters
     */
    protected String sanitizeUrlTemplate(String urlTemplate) {
        // FIXME generalize this on a per-exporter basis (Prometheus will have different requirements than Atlas, etc).
        String sanitized = urlTemplate
                .replaceAll("\\{(\\w+):.+}(?=/|$)", "-$1-") // extract path variable names from regex expressions
                .replaceAll("/", "_")
                .replaceAll("[{}]", "-");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "none";
        }
        return sanitized;
    }
}
