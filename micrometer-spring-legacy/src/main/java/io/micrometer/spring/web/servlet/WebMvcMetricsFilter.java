/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.web.servlet;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.spring.TimedUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.handler.MatchableHandlerMapping;
import org.springframework.web.util.NestedServletException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intercepts incoming HTTP requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@NonNullApi
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WebMvcMetricsFilter extends OncePerRequestFilter {
    private static final String TIMING_SAMPLE = "micrometer.timingSample";

    private static final Log logger = LogFactory.getLog(WebMvcMetricsFilter.class);

    private final MeterRegistry registry;
    private final WebMvcTagsProvider tagsProvider;
    private final String metricName;
    private final boolean autoTimeRequests;
    private final HandlerMappingIntrospector mappingIntrospector;

    public WebMvcMetricsFilter(MeterRegistry registry, WebMvcTagsProvider tagsProvider,
                               String metricName, boolean autoTimeRequests,
                               HandlerMappingIntrospector mappingIntrospector) {
        this.registry = registry;
        this.tagsProvider = tagsProvider;
        this.metricName = metricName;
        this.autoTimeRequests = autoTimeRequests;
        this.mappingIntrospector = mappingIntrospector;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HandlerExecutionChain handler = null;
        try {
            MatchableHandlerMapping matchableHandlerMapping = mappingIntrospector.getMatchableHandlerMapping(request);
            if (matchableHandlerMapping != null) {
                handler = matchableHandlerMapping.getHandler(request);
            }
        } catch (Exception e) {
            logger.debug("Unable to time request", e);
            filterChain.doFilter(request, response);
            return;
        }

        final Object handlerObject = handler == null ? null : handler.getHandler();

        // If this is the second invocation of the filter in an async request, we don't
        // want to start sampling again (effectively bumping the active count on any long task timers).
        // Rather, we'll just use the sampling context we started on the first invocation.
        TimingSampleContext timingContext = (TimingSampleContext) request.getAttribute(TIMING_SAMPLE);
        if (timingContext == null) {
            timingContext = new TimingSampleContext(request, handlerObject);
        }

        try {
            filterChain.doFilter(request, response);

            if (request.isAsyncSupported()) {
                // this won't be "started" until after the first call to doFilter
                if (request.isAsyncStarted()) {
                    request.setAttribute(TIMING_SAMPLE, timingContext);
                }
            }

            if (!request.isAsyncStarted()) {
                record(timingContext, response, request,
                    handlerObject, (Throwable) request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE));
            }
        } catch (NestedServletException e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            record(timingContext, response, request, handlerObject, e.getCause());
            throw e;
        } catch (ServletException | IOException | RuntimeException ex) {
            record(timingContext, response, request, handlerObject, ex);
            throw ex;
        }
    }

    private void record(TimingSampleContext timingContext, HttpServletResponse response, HttpServletRequest request,
                        Object handlerObject, Throwable e) {
        for (Timed timedAnnotation : timingContext.timedAnnotations) {
            timingContext.timerSample.stop(Timer.builder(timedAnnotation, metricName)
                .tags(tagsProvider.httpRequestTags(request, response, handlerObject, e))
                .register(registry));
        }

        if (timingContext.timedAnnotations.isEmpty() && autoTimeRequests) {
            timingContext.timerSample.stop(Timer.builder(metricName)
                .tags(tagsProvider.httpRequestTags(request, response, handlerObject, e))
                .register(registry));
        }

        for (LongTaskTimer.Sample sample : timingContext.longTaskTimerSamples) {
            sample.stop();
        }
    }

    private class TimingSampleContext {
        private final Set<Timed> timedAnnotations;
        private final Timer.Sample timerSample;
        private final Collection<LongTaskTimer.Sample> longTaskTimerSamples;

        TimingSampleContext(HttpServletRequest request, Object handlerObject) {
            timedAnnotations = annotations(handlerObject);
            timerSample = Timer.start(registry);
            longTaskTimerSamples = timedAnnotations.stream()
                .filter(Timed::longTask)
                .map(t -> LongTaskTimer.builder(t)
                    .tags(tagsProvider.httpLongRequestTags(request, handlerObject))
                    .register(registry)
                    .start())
                .collect(Collectors.toList());
        }

        private Set<Timed> annotations(Object handler) {
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                Set<Timed> timed = TimedUtils.findTimedAnnotations(handlerMethod.getMethod());
                if (timed.isEmpty()) {
                    return TimedUtils.findTimedAnnotations(handlerMethod.getBeanType());
                }
                return timed;
            }
            return Collections.emptySet();
        }
    }
}
