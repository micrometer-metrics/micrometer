/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.java17.instrument.binder.jdk;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Threshold;
import jdk.jfr.consumer.RecordedStackTrace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link JfrMeterBinder}.
 *
 * @author Szymon Habrainski
 */
class JfrMeterBinderTest {

    private static final String EVENT_WITH_THRESHOLD_NAME = "io.micrometer.java17.instrument.binder.jdk.JfrMeterBinderTests.EventWithThreshold";

    private static final String EVENT_WITHOUT_THRESHOLD_NAME = "io.micrometer.java17.instrument.binder.jdk.JfrMeterBinderTests.EventWithoutThreshold";

    private static final Duration EVENT_THRESHOLD = Duration.ofMillis(20);

    @Test
    void bindToConfiguresRegisteredEvents() throws InterruptedException {
        CountDownLatch eventWithoutThreshold = new CountDownLatch(1);
        CountDownLatch eventWithThreshold = new CountDownLatch(1);
        AtomicReference<RecordedStackTrace> stackTrace = new AtomicReference<>();

        try (TestJfrMeterBinder binder = new TestJfrMeterBinder(eventWithoutThreshold, eventWithThreshold,
                stackTrace)) {
            binder.bindTo(new SimpleMeterRegistry());
            await().atMost(Duration.ofSeconds(10))
                .until(() -> new EventWithThreshold().isEnabled() && new EventWithoutThreshold().isEnabled());

            EventWithoutThreshold withoutThreshold = new EventWithoutThreshold();
            assertThat(withoutThreshold.shouldCommit()).isTrue();
            withoutThreshold.commit();

            EventWithThreshold belowThreshold = new EventWithThreshold();
            belowThreshold.begin();
            belowThreshold.end();
            assertThat(belowThreshold.shouldCommit()).isFalse();
            belowThreshold.commit();

            EventWithThreshold aboveThreshold = new EventWithThreshold();
            aboveThreshold.begin();
            Thread.sleep(EVENT_THRESHOLD.plusMillis(50).toMillis());
            aboveThreshold.end();
            assertThat(aboveThreshold.shouldCommit()).isTrue();
            aboveThreshold.commit();

            assertThat(eventWithoutThreshold.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(eventWithThreshold.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(stackTrace.get()).isNull();
        }
    }

    private static class TestJfrMeterBinder extends JfrMeterBinder {

        private final CountDownLatch eventWithoutThreshold;

        private final CountDownLatch eventWithThreshold;

        private final AtomicReference<RecordedStackTrace> stackTrace;

        TestJfrMeterBinder(CountDownLatch eventWithoutThreshold, CountDownLatch eventWithThreshold,
                AtomicReference<RecordedStackTrace> stackTrace) {
            this.eventWithoutThreshold = eventWithoutThreshold;
            this.eventWithThreshold = eventWithThreshold;
            this.stackTrace = stackTrace;
        }

        @Override
        protected void bindTo(MeterRegistry registry, EventHandlerRegistrar eventHandlerRegistrar) {
            eventHandlerRegistrar.register(EVENT_WITHOUT_THRESHOLD_NAME, event -> eventWithoutThreshold.countDown());
            eventHandlerRegistrar.register(EVENT_WITH_THRESHOLD_NAME, event -> {
                stackTrace.set(event.getStackTrace());
                eventWithThreshold.countDown();
            }, EVENT_THRESHOLD);
        }

    }

    @Name(EVENT_WITH_THRESHOLD_NAME)
    static class EventWithThreshold extends Event {

    }

    @Name(EVENT_WITHOUT_THRESHOLD_NAME)
    @Threshold("1 s")
    static class EventWithoutThreshold extends Event {

    }

}
