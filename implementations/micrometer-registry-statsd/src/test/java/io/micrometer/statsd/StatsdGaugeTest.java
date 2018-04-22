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

    @SuppressWarnings("unchecked")
    private Subscriber<String> publisher = mock(Subscriber.class);

    @Test
    void shouldAlwaysPublishValue() {
        StatsdGauge<?> alwaysPublishingGauge = gauge(true);

        alwaysPublishingGauge.poll();
        alwaysPublishingGauge.poll();

        verify(publisher, times(2)).onNext(any());
    }

    @Test
    void shouldOnlyPublishValue_WhenValueChanges() {
        StatsdGauge<?> gaugePublishingOnChange = gauge(false);

        gaugePublishingOnChange.poll();
        gaugePublishingOnChange.poll();

        verify(publisher, times(1)).onNext(any());

        //update value and expect the publisher to be called again
        value.incrementAndGet();
        gaugePublishingOnChange.poll();


        verify(publisher, times(2)).onNext(any());
    }


    private StatsdGauge<?> gauge(boolean alwaysPublish) {
        Meter.Id meterId = new Meter.Id("test", Collections.emptyList(), null, null, Meter.Type.GAUGE);
        return new StatsdGauge<>(meterId, lineBuilder, publisher, value, AtomicInteger::get, alwaysPublish);
    }

}
