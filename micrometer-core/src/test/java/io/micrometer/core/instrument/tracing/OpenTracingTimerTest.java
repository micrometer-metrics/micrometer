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
package io.micrometer.core.instrument.tracing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTracingTimerTest {

    private MockTracer tracer;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup(){
        tracer = new MockTracer(new ThreadLocalActiveSpanSource(), MockTracer.Propagator.TEXT_MAP);
        registry = new SimpleMeterRegistry();
        registry.config().commonTags("commonTag1", "commonVal1");
    }

    @Test
    void tracingTimer() {
        OpenTracingTimer.builder("trace.outer.timer", tracer).tags("testTag1","testVal1").register(registry).record(() -> {
            OpenTracingTimer.builder("trace.inner.timer", tracer).tags("testTag2","testVal2").register(registry).record(() -> {
                //Nothing to do here
            });
        });

        List<MockSpan> mockSpans = tracer.finishedSpans();
        assertThat(mockSpans).hasSize(2);

        MockSpan innerSpan = mockSpans.get(0);
        assertThat(innerSpan.operationName()).isEqualTo("trace.inner.timer");
        assertThat(innerSpan.tags().get("commonTag1")).describedAs("Common tags are applied").isEqualTo("commonVal1");
        assertThat(innerSpan.parentId()).isEqualTo(2L);

        MockSpan outerSpan = mockSpans.get(1);
        assertThat(outerSpan.operationName()).isEqualTo("trace.outer.timer");
        assertThat(outerSpan.tags()).hasSize(2);
        assertThat(outerSpan.tags().get("testTag1")).isEqualTo("testVal1");
    }

}
