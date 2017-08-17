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
package io.micrometer.spring.web;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.quantile.WindowSketchQuantiles;
import io.micrometer.core.instrument.util.AnnotationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class ControllerMetrics {
    private static final String TIMING_REQUEST_ATTRIBUTE = "micrometer.requestStartTime";
    private static final String HANDLER_REQUEST_ATTRIBUTE = "micrometer.requestHandler";
    private static final String EXCEPTION_ATTRIBUTE = "micrometer.requestException";

    private static final Log logger = LogFactory.getLog(ControllerMetrics.class);

    private final MeterRegistry registry;
    private final WebmvcTagConfigurer tagConfigurer;
    private final String metricName;

    private final Map<HttpServletRequest, Long> longTaskTimerIds = Collections.synchronizedMap(new IdentityHashMap<>());

    public ControllerMetrics(MeterRegistry registry,
                             WebmvcTagConfigurer tagConfigurer,
                             String metricName) {
        this.registry = registry;
        this.tagConfigurer = tagConfigurer;
        this.metricName = metricName;
    }

    public void tagWithException(Throwable t) {
        RequestContextHolder.getRequestAttributes().setAttribute(EXCEPTION_ATTRIBUTE, t, RequestAttributes.SCOPE_REQUEST);
    }

    void preHandle(HttpServletRequest request, Object handler) {
        request.setAttribute(TIMING_REQUEST_ATTRIBUTE, System.nanoTime());
        request.setAttribute(HANDLER_REQUEST_ATTRIBUTE, handler);

        longTaskTimed(handler).forEach(t -> {
            if(t.value().isEmpty()) {
                if(handler instanceof HandlerMethod){
                    logger.warn("Unable to perform metrics timing on " + ((HandlerMethod) handler).getShortLogMessage() + ": @Timed annotation must have a value used to name the metric");
                } else {
                    logger.warn("Unable to perform metrics timing for request " + request.getRequestURI() + ": @Timed annotation must have a value used to name the metric");
                }
                return;
            }
            longTaskTimerIds.put(request, longTaskTimer(t, request, handler).start());
        });
    }

    HttpServletResponse record(HttpServletRequest request, HttpServletResponse response, Throwable ex) {
        Long startTime = (Long) request.getAttribute(TIMING_REQUEST_ATTRIBUTE);
        Object handler = request.getAttribute(HANDLER_REQUEST_ATTRIBUTE);

        long endTime = System.nanoTime();
        Throwable thrown = ex != null ? ex : (Throwable) request.getAttribute(EXCEPTION_ATTRIBUTE);

        // complete any LongTaskTimer tasks running for this method
        longTaskTimed(handler).forEach(t -> {
            if(!t.value().isEmpty()) {
                longTaskTimer(t, request, handler).stop(longTaskTimerIds.remove(request));
            }
        });

        // record Timer values
        timed(handler).forEach(t -> {
            String name = metricName;
            if (!t.value().isEmpty()) {
                name = t.value();
            }

            Timer.Builder timerBuilder = registry.timerBuilder(name)
                    .tags(tagConfigurer.httpRequestTags(request, response, thrown));

            String[] extraTags = t.extraTags();
            if (extraTags.length > 0) {
                if (extraTags.length % 2 != 0) {
                    if (logger.isErrorEnabled()) {
                        Method method = ((HandlerMethod) handler).getMethod();
                        String target = method.getDeclaringClass().getName() + "." + method.getName();
                        logger.error("@Timed extraTags array on method " + target + " size must be even, it is a set of key=value pairs");
                    }
                } else {
                    timerBuilder = timerBuilder.tags(IntStream.range(0, extraTags.length / 2)
                            .mapToObj(i -> Tag.of(extraTags[i], extraTags[i + 1]))
                            .collect(Collectors.toList()));
                }
            }

            if(t.quantiles().length > 0) {
                timerBuilder = timerBuilder.quantiles(WindowSketchQuantiles.quantiles(t.quantiles()).create());
            }

            timerBuilder.create().record(endTime - startTime, TimeUnit.NANOSECONDS);
        });

        return response;
    }

    private LongTaskTimer longTaskTimer(Timed t, HttpServletRequest request, Object handler) {
        Iterable<Tag> tags = tagConfigurer.httpLongRequestTags(request, handler);
        if(t.extraTags().length > 0) {
            tags = Stream.concat(stream(tags.spliterator(), false), Tags.zip(t.extraTags()).stream()).collect(toList());
        }
        return registry.more().longTaskTimer(t.value(), tags);
    }

    private Set<Timed> longTaskTimed(Object m) {
        if(!(m instanceof HandlerMethod))
            return Collections.emptySet();

        Set<Timed> timed = AnnotationUtils.findTimed(((HandlerMethod) m).getMethod()).filter(Timed::longTask).collect(toSet());
        if (timed.isEmpty()) {
            return AnnotationUtils.findTimed(((HandlerMethod) m).getBeanType()).filter(Timed::longTask).collect(toSet());
        }
        return timed;
    }

    private Set<Timed> timed(Object m) {
        if(!(m instanceof HandlerMethod))
            return Collections.emptySet();

        Set<Timed> timed = AnnotationUtils.findTimed(((HandlerMethod) m).getMethod()).filter(t -> !t.longTask()).collect(toSet());
        if (timed.isEmpty()) {
            return AnnotationUtils.findTimed(((HandlerMethod) m).getBeanType()).filter(t -> !t.longTask()).collect(toSet());
        }
        return timed;
    }
}
