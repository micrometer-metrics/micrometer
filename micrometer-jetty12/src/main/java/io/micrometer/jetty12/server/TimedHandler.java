/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.jetty12.server;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.EventsHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.Graceful;

import java.util.concurrent.CompletableFuture;

/**
 * Jetty 12 Metrics Handler.
 *
 * @author Jon Schneider
 * @author Joakim Erdfelt
 * @since 1.13.0
 */
public class TimedHandler extends EventsHandler implements Graceful {

    private static final String SAMPLE_TIMER_ATTRIBUTE = "__micrometer_timer_sample";

    private static final String SAMPLE_REQUEST_LONG_TASK_TIMER_ATTRIBUTE = "__micrometer_request_ltt_sample";

    private static final String SAMPLE_HANDLER_LONG_TASK_TIMER_ATTRIBUTE = "__micrometer_handler_ltt_sample";

    protected static final String RESPONSE_STATUS_ATTRIBUTE = "__micrometer_jetty_core_response_status";

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    private final JettyCoreRequestTagsProvider tagsProvider;

    private final Shutdown shutdown = new Shutdown(this) {
        @Override
        public boolean isShutdownDone() {
            return timerRequest.activeTasks() == 0;
        }
    };

    /**
     * Full Request LifeCycle (inside and out of Handlers)
     */
    private final LongTaskTimer timerRequest;

    /**
     * How many Requests are inside handle() calls.
     */
    private final LongTaskTimer timerHandle;

    public TimedHandler(MeterRegistry registry, Iterable<Tag> tags) {
        this(registry, tags, new DefaultJettyCoreRequestTagsProvider());
    }

    public TimedHandler(MeterRegistry registry, Iterable<Tag> tags, JettyCoreRequestTagsProvider tagsProvider) {
        this.registry = registry;
        this.tags = tags;
        this.tagsProvider = tagsProvider;

        this.timerRequest = LongTaskTimer.builder("jetty.server.requests.open")
            .description("Jetty requests that are currently in progress")
            .tags(tags)
            .register(registry);

        this.timerHandle = LongTaskTimer.builder("jetty.server.handling.open")
            .description("Jetty requests inside handle() calls")
            .tags(tags)
            .register(registry);
    }

    @Override
    protected void onBeforeHandling(Request request) {
        beginRequestTiming(request);
        beginHandlerTiming(request);
        super.onBeforeHandling(request);
    }

    @Override
    protected void onAfterHandling(Request request, boolean handled, Throwable failure) {
        stopHandlerTiming(request);
        super.onAfterHandling(request, handled, failure);
    }

    @Override
    protected void onResponseBegin(Request request, int status, HttpFields headers) {
        // If we see a status of 0 here, that mean the Handler hasn't set a status code.
        request.setAttribute(RESPONSE_STATUS_ATTRIBUTE, status);
        super.onResponseBegin(request, status, headers);
    }

    @Override
    protected void onComplete(Request request, Throwable failure) {
        stopRequestTiming(request);
        super.onComplete(request, failure);
    }

    private void beginRequestTiming(Request request) {
        LongTaskTimer.Sample requestSample = timerRequest.start();
        request.setAttribute(SAMPLE_REQUEST_LONG_TASK_TIMER_ATTRIBUTE, requestSample);
    }

    private void stopRequestTiming(Request request) {
        Timer.Sample sample = getTimerSample(request);
        LongTaskTimer.Sample requestSample = (LongTaskTimer.Sample) request
            .getAttribute(SAMPLE_REQUEST_LONG_TASK_TIMER_ATTRIBUTE);
        if (requestSample == null)
            return; // timing complete

        sample.stop(Timer.builder("jetty.server.requests")
            .description("HTTP requests to the Jetty server")
            .tags(tagsProvider.getTags(request))
            .tags(tags)
            .register(registry));

        request.removeAttribute(SAMPLE_REQUEST_LONG_TASK_TIMER_ATTRIBUTE);

        requestSample.stop();
    }

    private void beginHandlerTiming(Request request) {
        LongTaskTimer.Sample handlerSample = timerHandle.start();
        request.setAttribute(SAMPLE_HANDLER_LONG_TASK_TIMER_ATTRIBUTE, handlerSample);
    }

    private void stopHandlerTiming(Request request) {
        Timer.Sample sample = getTimerSample(request);
        LongTaskTimer.Sample handlerSample = (LongTaskTimer.Sample) request
            .getAttribute(SAMPLE_HANDLER_LONG_TASK_TIMER_ATTRIBUTE);
        if (handlerSample == null)
            return; // timing complete

        sample.stop(Timer.builder("jetty.server.handling")
            .description("Requests being processed by Jetty handlers")
            .tags(tagsProvider.getTags(request))
            .tags(tags)
            .register(registry));

        request.removeAttribute(SAMPLE_HANDLER_LONG_TASK_TIMER_ATTRIBUTE);

        handlerSample.stop();
    }

    private Timer.Sample getTimerSample(Request request) {
        return (Timer.Sample) request.getAttribute(SAMPLE_TIMER_ATTRIBUTE);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        Timer.Sample sample = Timer.start(registry);
        request.setAttribute(SAMPLE_TIMER_ATTRIBUTE, sample);
        return super.handle(request, response, callback);
    }

    @Override
    protected void doStart() throws Exception {
        shutdown.cancel();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        shutdown.cancel();
        super.doStop();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return shutdown.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.isShutdown();
    }

    protected Shutdown getShutdown() {
        return shutdown;
    }

}
