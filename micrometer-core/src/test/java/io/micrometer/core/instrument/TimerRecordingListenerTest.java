package io.micrometer.core.instrument;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.test.TestSpanHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimerRecordingListenerTest {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    TestSpanHandler spans = new TestSpanHandler();
    Tracing tracing = Tracing.newBuilder().localServiceName(this.getClass().getSimpleName()).addSpanHandler(spans).build();

    @BeforeEach
    void setup() {
        meterRegistry.config().timerRecordingListener(new BraveTimerRecordingListener(tracing.tracer()));
    }

    @Test
    void timerAndSpansProduced() throws InterruptedException {
        Timer.Sample sample = Timer.start(meterRegistry);
        pause();
        sample.stop(Timer.builder("payment.processing").tags("method", "credit card").register(meterRegistry));

        // timer
        Timer actual = meterRegistry.get("payment.processing").timer();
        assertThat(actual.getId().getTags()).containsExactly(Tag.of("method", "credit card"));
        assertThat(actual.count()).isOne();
        assertThat(actual.totalTime(TimeUnit.NANOSECONDS)).isPositive();

        // span
        MutableSpan span = spans.get(0);
        assertThat(span.name()).isEqualTo("payment.processing");
        assertThat(span.tags()).containsOnlyKeys("method").containsValue("credit card");
        assertThat(span.finishTimestamp()).isNotZero();
    }

    void pause() throws InterruptedException {
        Thread.sleep(new Random().nextInt(2000));
    }

    static class BraveTimerRecordingListener implements TimerRecordingListener {
        final Tracer tracer;
        // TODO not specific to this listener but leaks are possible where onStart is called but onStop is not
        ConcurrentMap<Timer.Sample, SpanContext> contextMap = new ConcurrentHashMap<>();

        BraveTimerRecordingListener(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void onStart(Timer.Sample sample) {
            Span span = tracer.nextSpan().start();
            contextMap.computeIfAbsent(sample, key -> new SpanContext(span, tracer.withSpanInScope(span)));
        }

        @Override
        public void onError(Timer.Sample sample, Throwable throwable) {
            contextMap.get(sample).getSpan().error(throwable);
        }

        @Override
        public void onStop(Timer.Sample sample, Timer timer, Duration duration) {
            SpanContext context = contextMap.get(sample);
            Span span = context.getSpan().name(timer.getId().getName());
            timer.getId().getTagsAsIterable().forEach(tag -> span.tag(tag.getKey(), tag.getValue()));
            context.getSpanInScope().close();
            span.finish();
        }

        static class SpanContext {
            private final Span span;
            private final Tracer.SpanInScope spanInScope;

            SpanContext(Span span, Tracer.SpanInScope spanInScope) {
                this.span = span;
                this.spanInScope = spanInScope;
            }

            Span getSpan() {
                return span;
            }

            Tracer.SpanInScope getSpanInScope() {
                return spanInScope;
            }
        }
    }
}
