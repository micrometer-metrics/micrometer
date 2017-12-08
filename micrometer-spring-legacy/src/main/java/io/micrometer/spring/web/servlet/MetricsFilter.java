/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.web.servlet;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.handler.MatchableHandlerMapping;
import org.springframework.web.util.NestedServletException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Intercepts incoming HTTP requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MetricsFilter extends OncePerRequestFilter {
    private final WebMvcMetrics webMvcMetrics;
    private final HandlerMappingIntrospector mappingIntrospector;
    private final Logger logger = LoggerFactory.getLogger(MetricsFilter.class);

    public MetricsFilter(WebMvcMetrics webMvcMetrics, HandlerMappingIntrospector mappingIntrospector) {
        this.webMvcMetrics = webMvcMetrics;
        this.mappingIntrospector = mappingIntrospector;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HandlerExecutionChain handler;
        try {
            MatchableHandlerMapping matchableHandlerMapping = mappingIntrospector.getMatchableHandlerMapping(request);
            handler = matchableHandlerMapping.getHandler(request);
        } catch (Exception e) {
            logger.debug("Unable to time request", e);
            filterChain.doFilter(request, response);
            return;
        }

        if (handler != null) {
            Object handlerObject = handler.getHandler();
            if (handlerObject != null) {
                this.webMvcMetrics.preHandle(request, handlerObject);
                try {
                    filterChain.doFilter(request, response);

                    // when an async operation is complete, the whole filter gets called again with isAsyncStarted = false
                    if(!request.isAsyncStarted()) {
                        this.webMvcMetrics.record(request, response, null);
                    }
                } catch (NestedServletException e) {
                    response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    this.webMvcMetrics.record(request, response, e.getCause());
                    throw e;
                }
            }
        }
        else {
            filterChain.doFilter(request, response);
        }
    }
}
