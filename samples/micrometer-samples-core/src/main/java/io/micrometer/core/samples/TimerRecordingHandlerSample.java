/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.samples;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.core.lang.Nullable;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class TimerRecordingHandlerSample {
    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static void main(String[] args) throws InterruptedException {
        registry.config().timerRecordingHandler(new SampleHandler());
        Timer.Builder timerBuilder = Timer.builder("sample.timer").tag("a", "1");

        Timer.Sample sample = Timer.start(registry, new CustomHandlerContext());
        try (Timer.Scope scope = sample.makeCurrent()) {
            Thread.sleep(1_000);
            sample.error(new IOException("simulated"));
        }
        sample.stop(timerBuilder);

        Timer.start(registry).stop(timerBuilder);
        Timer.start(registry, new UnsupportedHandlerContext()).stop(timerBuilder);

        System.out.println();
        System.out.println(registry.scrape());
    }

    static class SampleHandler implements TimerRecordingHandler<CustomHandlerContext> {
        @Override
        public void onStart(Timer.Sample sample, @Nullable CustomHandlerContext context) {
            System.out.println("start: " + sample + " " + context);
        }

        @Override
        public void onError(Timer.Sample sample, @Nullable CustomHandlerContext context, Throwable throwable) {
            System.out.println("error: " + throwable + " " + sample + " " + context);
        }

        @Override
        public void onStop(Timer.Sample sample, @Nullable CustomHandlerContext context, Timer timer, Duration duration) {
            System.out.println("stop: " + duration + " " + toString(timer) + " " + sample + " " + context);
        }

        @Override
        public boolean supportsContext(@Nullable Timer.HandlerContext handlerContext) {
            return handlerContext instanceof CustomHandlerContext;
        }

        private String toString(Timer timer) {
            return timer.getId().getName() + " " + timer.getId().getTags();
        }

        @Override
        public void onScopeOpened(Sample sample, CustomHandlerContext context) {
            // TODO Auto-generated method stub
        }
    }

    static class CustomHandlerContext extends Timer.HandlerContext {
        private final UUID uuid = UUID.randomUUID();

        @Override
        public Tags getLowCardinalityTags() {
            return Tags.of("status", "ok");
        }

        @Override
        public Tags getHighCardinalityTags() {
            return Tags.of("userId", uuid.toString());
        }

        @Override
        public String toString() {
            return "CustomHandlerContext{" +
                    "uuid=" + uuid + ", " +
                    getAllTags() +
                    '}';
        }
    }

    static class UnsupportedHandlerContext extends Timer.HandlerContext {
        @Override
        public String toString() {
            return "sorry";
        }
    }
}
