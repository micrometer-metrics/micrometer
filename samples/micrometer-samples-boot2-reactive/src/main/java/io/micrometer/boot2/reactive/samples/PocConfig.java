/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.boot2.reactive.samples;

import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.TimerRecordingListener;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Logger;

/**
 * This should be made into an auto-configuration. It is temporarily here to wire things up for this sample.
 */
@Configuration
class PocConfig {

    @Bean
    MeterRegistryCustomizer<PrometheusMeterRegistry> configureListeners(ObjectProvider<TimerRecordingListener> listeners) {
        return registry -> listeners.forEach(listener -> registry.config().timerRecordingListener(listener));
    }

    @Bean
    LogSpanHandler logSpanHandler() {
        return new LogSpanHandler();
    }

    @Bean
    Tracing tracing(SpanHandler spanHandler) {
        return Tracing.newBuilder().localServiceName("boot2-reactive-sample").sampler(Sampler.ALWAYS_SAMPLE).addSpanHandler(spanHandler).build();
    }

    @Bean
    Tracer tracer(Tracing tracing) {
        return tracing.tracer();
    }

    @Bean
    BraveTimerRecordingListener braveTimerRecordingListener(Tracer tracer) {
        return new BraveTimerRecordingListener(tracer);
    }

    // Copied from Brave
    static final class LogSpanHandler extends SpanHandler {
        final Logger logger = Logger.getLogger(LogSpanHandler.class.getName());

        @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            logger.info(span.toString());
            return true;
        }

        @Override public String toString() {
            return "LogSpanHandler{name=" + logger.getName() + "}";
        }
    }

}
