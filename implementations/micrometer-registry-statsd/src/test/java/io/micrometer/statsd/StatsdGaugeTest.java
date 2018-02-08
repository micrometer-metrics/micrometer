package io.micrometer.statsd;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

class StatsdGaugeTest {


    private AtomicInteger value = new AtomicInteger(1);

    private StatsdLineBuilder lineBuilder = mock(StatsdLineBuilder.class);
    private Subscriber<String> publisher = mock(Subscriber.class);

    @Test
    void shouldAlwaysPublishValue() {
        StatsdGauge<?> alwaysPublishingGauge = gauge(true);

        alwaysPublishingGauge.poll();
        alwaysPublishingGauge.poll();

        verify(publisher, times(2)).onNext(any());
    }

    @Test
    void shoulOnlyPublishValue_WhenValueChanges() {
        StatsdGauge<?> guagePublishingOnChange = gauge(false);

        guagePublishingOnChange.poll();
        guagePublishingOnChange.poll();

        verify(publisher, times(1)).onNext(any());

        //update value and expect the publisher to be called again
        value.incrementAndGet();
        guagePublishingOnChange.poll();


        verify(publisher, times(2)).onNext(any());
    }


    private StatsdGauge<?> gauge(boolean alwaysPublish) {
        Meter.Id meterId = new Meter.Id("test", Collections.emptyList(), null, null, Meter.Type.GAUGE);
        return new StatsdGauge<>(meterId, lineBuilder, publisher, value, AtomicInteger::get, alwaysPublish);
    }

}