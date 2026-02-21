/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.common.KeyValue;
import org.jspecify.annotations.Nullable;

/**
 * Context that contains the necessary information to create an exemplar.
 *
 * @author Jonatan Ivanov
 * @since 1.17.0
 */
public class OtlpExemplarContext {

    private final @Nullable String traceId;

    private final @Nullable String spanId;

    private final @Nullable Iterable<KeyValue> keyValues;

    /**
     * @param traceId the TraceId of the Span
     * @param spanId the SpanId of the Span
     */
    public OtlpExemplarContext(String traceId, String spanId) {
        this(traceId, spanId, null);
    }

    /**
     * @param traceId the TraceId of the Span
     * @param spanId the SpanId of the Span
     * @param keyValues arbitrary key-value pairs to attach to the exemplar
     */
    public OtlpExemplarContext(@Nullable String traceId, @Nullable String spanId,
            @Nullable Iterable<KeyValue> keyValues) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.keyValues = keyValues;
    }

    public @Nullable String getTraceId() {
        return traceId;
    }

    public @Nullable String getSpanId() {
        return spanId;
    }

    public @Nullable Iterable<KeyValue> getKeyValues() {
        return keyValues;
    }

}
