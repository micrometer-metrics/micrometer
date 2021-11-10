/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.samples;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.TimerRecordingListener;
import io.micrometer.core.lang.Nullable;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class TimerRecordingListenerSample {
    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static void main(String[] args) throws InterruptedException {
        registry.config().timerRecordingListener(new SampleListener());
        Timer timer = Timer.builder("sample.timer")
                .tag("a", "1")
                .register(registry);

        Timer.Sample sample = Timer.start(registry, new CustomContext());
        Thread.sleep(1_000);
        sample.error(new IOException("simulated"));
        sample.stop(timer);

        Timer.start(registry).stop(timer);
        Timer.start(registry, null).stop(timer);
        Timer.start(registry, new UnsupportedContext()).stop(timer);
    }

    static class SampleListener implements TimerRecordingListener<CustomContext> {
        @Override
        public void onStart(Timer.Sample sample, @Nullable CustomContext context) {
            System.out.println("start: " + sample + " " + context);
        }

        @Override
        public void onError(Timer.Sample sample, @Nullable CustomContext context, Throwable throwable) {
            System.out.println("error: " + throwable + " " + sample + " " + context);
        }

        @Override
        public void onStop(Timer.Sample sample, @Nullable CustomContext context, Timer timer, Duration duration) {
            System.out.println("stop: " + duration + " " + toString(timer) + " " + sample + " " + context);
        }

        @Override
        public boolean supportsContext(@Nullable Timer.Context context) {
            return context != null && context.getClass().isAssignableFrom(CustomContext.class);
        }

        private String toString(Timer timer) {
            return timer.getId().getName() + " " + timer.getId().getTags();
        }

        @Override
        public void onRestore(Sample sample, CustomContext context) {
            // TODO Auto-generated method stub
            
        }
    }

    static class CustomContext extends Timer.Context {
        private final UUID uuid = UUID.randomUUID();

        @Override
        public String toString() {
            return "CustomContext{" +
                    "uuid=" + uuid +
                    '}';
        }
    }

    static class UnsupportedContext extends Timer.Context {
        @Override
        public String toString() {
            return "sorry";
        }
    }
}
