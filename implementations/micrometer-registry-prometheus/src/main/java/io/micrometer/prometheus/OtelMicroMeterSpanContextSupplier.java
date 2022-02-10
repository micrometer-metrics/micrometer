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
package io.micrometer.prometheus;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import io.prometheus.client.exemplars.tracer.otel.OpenTelemetrySpanContextSupplier;

// A context supplier that also give information about sampling or not of current trace
public class OtelMicroMeterSpanContextSupplier implements SpanContextSupplier {

    public static boolean isAvailable() {
        try {
            if ("inactive".equalsIgnoreCase(System.getProperties().getProperty("io.prometheus.otelExemplars"))) {
                return false;
            }
            OpenTelemetrySpanContextSupplier test = new OpenTelemetrySpanContextSupplier();
            test.getSpanId();
            test.getTraceId();
            return true;
        } catch (LinkageError ignored) {
            // NoClassDefFoundError:
            // Either OpenTelemetry is not present, or it is version 0.9.1 or older when
            // io.opentelemetry.api.trace.Span did not exist.
            // IncompatibleClassChangeError:
            // The application uses an OpenTelemetry version between 0.10.0 and 0.15.0 when
            // SpanContext was a class, and not an interface.
            return false;
        }
    }

    @Override
    public String getTraceId() {
        String traceId = Span.current().getSpanContext().getTraceId();
        return TraceId.isValid(traceId) ? traceId : null;
    }

    @Override
    public String getSpanId() {
        String spanId = Span.current().getSpanContext().getSpanId();
        return SpanId.isValid(spanId) ? spanId : null;
    }

    public boolean isSampled() {
        return Span.current().getSpanContext().isSampled();
    }

}
