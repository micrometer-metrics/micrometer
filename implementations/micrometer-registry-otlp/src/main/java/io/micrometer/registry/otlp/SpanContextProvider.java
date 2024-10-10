package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;

public interface SpanContextProvider {

    @Nullable
    SpanContext gerCurrentSpan();

    class SpanContext {
        private final String traceId;
        private final String spanId;
        private final boolean isSpanSampled;

        public SpanContext(String traceId, String spanId, boolean isSpanSampled) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.isSpanSampled = isSpanSampled;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public boolean isSpanSampled() {
            return isSpanSampled;
        }
    }
}
