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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.instrument.internal.TimedUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

/**
 * Intercepts incoming HTTP requests and records metrics about execution time and results.
 *
 * @author Jon Schneider
 */
public class WebmvcMetricsHandlerInterceptor extends HandlerInterceptorAdapter {

    private static final Log logger = LogFactory.getLog(WebmvcMetricsHandlerInterceptor.class);
    private static final String TIMING_REQUEST_ATTRIBUTE = "requestStartTime";

    private final MeterRegistry registry;
    private final WebMetricsTagProvider provider;
    private final String metricName;

    private final Map<Timed, Long> longTaskTimerIds = new ConcurrentHashMap<>();

    public WebmvcMetricsHandlerInterceptor(MeterRegistry registry,
                                           WebMetricsTagProvider provider,
                                           String metricName) {
        this.registry = registry;
        this.provider = provider;
        this.metricName = metricName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        longTaskTimed(handler).forEach(t -> {
            if(t.value().isEmpty()) {
                logger.warn("Unable to perform metrics timing on " + ((HandlerMethod) handler).getShortLogMessage() + ": @Timed annotation must have a value used to name the metric");
                return;
            }
            longTaskTimerIds.put(t, registry.longTaskTimer(t.value(), provider.httpLongRequestTags(request, handler)).start());
        });

        RequestContextHolder.getRequestAttributes().setAttribute(TIMING_REQUEST_ATTRIBUTE,
                System.nanoTime(), SCOPE_REQUEST);
        return super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        RequestContextHolder.getRequestAttributes().setAttribute("exception", ex,
                SCOPE_REQUEST);

        Long startTime = (Long) RequestContextHolder.getRequestAttributes().getAttribute(
                TIMING_REQUEST_ATTRIBUTE, SCOPE_REQUEST);
        if (startTime != null)
            recordMetric(request, response, handler, startTime);

        super.afterCompletion(request, response, handler, ex);
    }

    private void recordMetric(HttpServletRequest request, HttpServletResponse response,
                              Object handler, Long startTime) {
        long endTime = System.nanoTime();

        // complete any LongTaskTimer tasks running for this method
        longTaskTimed(handler).forEach(t -> {
            if(!t.value().isEmpty()) {
                registry.longTaskTimer(t.value(), provider.httpLongRequestTags(request, handler)).stop(longTaskTimerIds.remove(t));
            }
        });

        // record Timer values
        timed(handler).forEach(t -> {
            String name = metricName;
            if (!t.value().isEmpty()) {
                name = t.value();
            }

            Stream<Tag> tags = provider.httpRequestTags(request, response, handler);
            String[] extraTags = t.extraTags();
            if (extraTags.length > 0) {
                if (extraTags.length % 2 != 0) {
                    if (logger.isErrorEnabled()) {
                        Method method = ((HandlerMethod) handler).getMethod();
                        String target = method.getDeclaringClass().getName() + "." + method.getName();
                        logger.error("@Timed extraTags array on method " + target + " size must be even, it is a set of key=value pairs");
                    }
                } else {
                    Stream<Tag> extraTagsStream = IntStream.range(0, extraTags.length / 2)
                            .mapToObj(i -> Tag.of(extraTags[i], extraTags[i + 1]));
                    tags = Stream.concat(tags, extraTagsStream);
                }
            }

            Timer timer = registry.timer(name, tags);
            if (timer != null) {
                timer.record(endTime - startTime, TimeUnit.NANOSECONDS);
            }
        });
    }

    private Set<Timed> longTaskTimed(Object m) {
        if(!(m instanceof HandlerMethod))
            return Collections.emptySet();

        Set<Timed> timed = TimedUtils.findTimed(((HandlerMethod) m).getMethod()).filter(Timed::longTask).collect(toSet());
        if (timed.isEmpty()) {
            return TimedUtils.findTimed(((HandlerMethod) m).getBeanType()).filter(Timed::longTask).collect(toSet());
        }
        return timed;
    }

    private Set<Timed> timed(Object m) {
        if(!(m instanceof HandlerMethod))
            return Collections.emptySet();

        Set<Timed> timed = TimedUtils.findTimed(((HandlerMethod) m).getMethod()).filter(t -> !t.longTask()).collect(toSet());
        if (timed.isEmpty()) {
            return TimedUtils.findTimed(((HandlerMethod) m).getBeanType()).filter(t -> !t.longTask()).collect(toSet());
        }
        return timed;
    }
}
