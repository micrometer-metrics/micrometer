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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.spring.TimedUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Support class for Spring MVC metrics.
 *
 * @author Jon Schneider
 */
public class WebMvcMetrics {

    private static final String TIMING_REQUEST_ATTRIBUTE = "micrometer.requestStartTime";

    private static final String HANDLER_REQUEST_ATTRIBUTE = "micrometer.requestHandler";

    private static final String EXCEPTION_ATTRIBUTE = "micrometer.requestException";

    private final Map<HttpServletRequest, Collection<LongTaskTimer.Sample>> longTaskTimings = Collections
        .synchronizedMap(new IdentityHashMap<>());

    private final MeterRegistry registry;

    private final Clock clock;

    private final WebMvcTagsProvider tagsProvider;

    private final String metricName;

    private final boolean autoTimeRequests;

    private final boolean percentileHistogram;

    public WebMvcMetrics(MeterRegistry registry, WebMvcTagsProvider tagsProvider,
                         String metricName, boolean autoTimeRequests, boolean percentileHistogram) {
        this.registry = registry;
        this.clock = registry.config().clock();
        this.tagsProvider = tagsProvider;
        this.metricName = metricName;
        this.autoTimeRequests = autoTimeRequests;
        this.percentileHistogram = percentileHistogram;
    }

    public void tagWithException(Throwable exception) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        attributes.setAttribute(EXCEPTION_ATTRIBUTE, exception,
            RequestAttributes.SCOPE_REQUEST);
    }

    void preHandle(HttpServletRequest request, Object handler) {
        if (request.getAttribute(TIMING_REQUEST_ATTRIBUTE) == null) {
            request.setAttribute(TIMING_REQUEST_ATTRIBUTE, clock.monotonicTime());
        }

        request.setAttribute(HANDLER_REQUEST_ATTRIBUTE, handler);
        Collection<LongTaskTimer.Sample> longTaskSamples = longTaskTimers(handler).stream().map(t -> t.register(registry).start())
            .collect(Collectors.toList());
        if(!longTaskSamples.isEmpty()) {
            this.longTaskTimings.put(request, longTaskSamples);
        }
    }

    void record(HttpServletRequest request, HttpServletResponse response, Throwable ex) {
        completeLongTaskTimers(request);

        Object handler = request.getAttribute(HANDLER_REQUEST_ATTRIBUTE);
        Long startTime = (Long) request.getAttribute(TIMING_REQUEST_ATTRIBUTE);
        Throwable thrown = ex != null ? ex : (Throwable) request.getAttribute(EXCEPTION_ATTRIBUTE);
        completeShortTimers(request, response, handler, startTime, clock.monotonicTime(), thrown);
    }

    private void completeLongTaskTimers(HttpServletRequest request) {
        Collection<LongTaskTimer.Sample> samples = this.longTaskTimings.remove(request);
        if (samples != null) {
            for (LongTaskTimer.Sample sample : samples) {
                sample.stop();
            }
        }
    }

    private void completeShortTimers(HttpServletRequest request, HttpServletResponse response,
                                     Object handler, long startTime, long endTime, Throwable thrown) {
        final long time = endTime - startTime;

        // record Timer values
        for (Timer.Builder builder : shortTimers(handler)) {
            builder
                .tags(this.tagsProvider.httpRequestTags(request, response, thrown))
                .register(this.registry)
                .record(time, TimeUnit.NANOSECONDS);
        }
    }

    private Set<LongTaskTimer.Builder> longTaskTimers(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Set<LongTaskTimer.Builder> timed = longTimersFromAnnotatedElement(handlerMethod.getMethod());
            if (timed.isEmpty()) {
                return longTimersFromAnnotatedElement(handlerMethod.getBeanType());
            }
            return timed;
        }
        return Collections.emptySet();
    }

    private Set<Timer.Builder> shortTimers(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;

            Set<Timer.Builder> timers = shortTimersFromAnnotatedElement(handlerMethod.getMethod());
            if (timers.isEmpty()) {
                timers = shortTimersFromAnnotatedElement(handlerMethod.getBeanType());
                if (timers.isEmpty() && this.autoTimeRequests) {
                    return Collections.singleton(Timer.builder(metricName)
                        .publishPercentileHistogram(this.percentileHistogram));
                }
                return timers;
            }
        } else if (handler instanceof ResourceHttpRequestHandler && this.autoTimeRequests) {
            return Collections.singleton(Timer.builder(metricName)
                .publishPercentileHistogram(this.percentileHistogram));
        }
        return Collections.emptySet();
    }

    private Set<Timer.Builder> shortTimersFromAnnotatedElement(AnnotatedElement element) {
        return TimedUtils.findTimedAnnotations(element).filter(t -> !t.longTask())
            .map(t -> Timer.builder(t, metricName)).collect(Collectors.toSet());
    }

    private Set<LongTaskTimer.Builder> longTimersFromAnnotatedElement(AnnotatedElement element) {
        return TimedUtils.findTimedAnnotations(element).filter(Timed::longTask)
            .map(LongTaskTimer::builder).collect(Collectors.toSet());
    }
}
