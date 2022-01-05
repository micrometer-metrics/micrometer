/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.boot2.reactive.samples.boot.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.metrics.AutoTimer;
import org.springframework.boot.actuate.metrics.web.reactive.client.WebClientExchangeTagsProvider;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copy of {@link org.springframework.boot.actuate.metrics.web.reactive.client.MetricsWebClientFilterFunction}.
 * Uses Timer start/stop instead of passing duration.
 */
public class PocMetricsWebClientFilterFunction implements ExchangeFilterFunction {

    private final String METRICS_WEBCLIENT_SAMPLE = PocMetricsWebClientFilterFunction.class.getName()
            + ".SAMPLE";

    private final MeterRegistry meterRegistry;

    private final WebClientExchangeTagsProvider tagProvider;

    private final String metricName;

    private final AutoTimer autoTimer;

    /**
     * Create a new {@code MetricsWebClientFilterFunction}.
     *
     * @param meterRegistry the registry to which metrics are recorded
     * @param tagProvider   provider for metrics tags
     * @param metricName    name of the metric to record
     * @param autoTimer     the auto-timer configuration or {@code null} to disable
     * @since 2.2.0
     */
    public PocMetricsWebClientFilterFunction(MeterRegistry meterRegistry, WebClientExchangeTagsProvider tagProvider,
                                             String metricName, AutoTimer autoTimer) {
        this.meterRegistry = meterRegistry;
        this.tagProvider = tagProvider;
        this.metricName = metricName;
        this.autoTimer = (autoTimer != null) ? autoTimer : AutoTimer.DISABLED;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (!this.autoTimer.isEnabled()) {
            return next.exchange(request);
        }
        return next.exchange(request).as((responseMono) -> instrumentResponse(request, responseMono))
                .contextWrite(this::putSample);
    }

    private Mono<ClientResponse> instrumentResponse(ClientRequest request, Mono<ClientResponse> responseMono) {
        final AtomicBoolean responseReceived = new AtomicBoolean();
        return Mono.deferContextual((ctx) -> responseMono.doOnEach((signal) -> {
            if (signal.isOnNext() || signal.isOnError()) {
                responseReceived.set(true);
                Iterable<Tag> tags = this.tagProvider.tags(request, signal.get(), signal.getThrowable());
                recordTimer(tags, getSample(ctx));
            }
        }).doFinally((signalType) -> {
            if (!responseReceived.get() && SignalType.CANCEL.equals(signalType)) {
                Iterable<Tag> tags = this.tagProvider.tags(request, null, null);
                recordTimer(tags, getSample(ctx));
            }
        }));
    }

    private void recordTimer(Iterable<Tag> tags, Timer.Sample sample) {
        sample.stop(this.autoTimer.builder(this.metricName).tags(tags).description("Timer of WebClient operation")
                .register(this.meterRegistry));
    }

    private Timer.Sample getSample(ContextView context) {
        return context.get(METRICS_WEBCLIENT_SAMPLE);
    }

    private Context putSample(Context context) {
        return context.put(METRICS_WEBCLIENT_SAMPLE, Timer.start(this.meterRegistry));
    }
}
