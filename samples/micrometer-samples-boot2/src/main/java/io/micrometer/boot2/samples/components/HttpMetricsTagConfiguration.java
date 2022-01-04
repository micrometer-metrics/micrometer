/*
 * Copyright 2020 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.boot2.samples.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.boot.actuate.metrics.web.servlet.DefaultWebMvcTagsProvider;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Demonstrates how to add custom tags based on the contents of the response body.
 */
@Configuration
public class HttpMetricsTagConfiguration {
    private final Map<HttpServletResponse, Tags> responseTags = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .<HttpServletResponse, Tags>build()
            .asMap();

    @Bean
    OncePerRequestFilter extractCountry() {
        return new OncePerRequestFilter() {
            private final ObjectMapper mapper = new ObjectMapper();

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                ContentCachingResponseWrapper cached = new ContentCachingResponseWrapper(response);
                filterChain.doFilter(request, cached);

                Object path = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if (path.equals("/api/person/{id}")) {
                    // Prometheus requires the same tags on all `http.server.requests`. So we'll need to add
                    // a `@Timed("person.requests") to the /api/person/{id} endpoint so it has a different name.
                    Person person = mapper.readValue(cached.getContentAsByteArray(), Person.class);
                    responseTags.put(response, Tags.of("country", person.getCountry()));
                }

                cached.copyBodyToResponse();
            }
        };
    }

    @Bean
    WebMvcTagsProvider webMvcTagsProvider() {
        return new DefaultWebMvcTagsProvider() {
            @Override
            public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Throwable exception) {
                return Tags.concat(
                        super.getTags(request, response, handler, exception),
                        Optional.ofNullable(responseTags.remove(response)).orElse(Tags.empty())
                );
            }
        };
    }
}
