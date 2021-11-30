/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcMetricsTest {
    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());


    @Test
    void nonRenamedMetrics() throws Exception {

        MetricCollectingClientInterceptor metricCollectingClientInterceptor = new MetricCollectingClientInterceptor(registry);
        runARequest(metricCollectingClientInterceptor);

        Tags expectedTags = Tags.of(
                "method", "bare",
                "methodType", "UNARY",
                "service", "service"
        );

        assertThat( registry.get("grpc.client.responses.received").tags(expectedTags).counter().count()).isEqualTo(1.0);
        assertThat( registry.get("grpc.client.requests.sent").tags(expectedTags).counter().count()).isEqualTo(1.0);
        Timer clientTimer = registry.get("grpc.client.processing.duration").tags(expectedTags).timer();
        assertThat(clientTimer.getId().getTag("statusCode")).isEqualTo("OK");
        assertThat( clientTimer.count()).isEqualTo(1);
    }
    @Test
    void renamedMetrics() throws Exception {

        MetricCollectingClientInterceptor metricCollectingClientInterceptor = new MetricCollectingClientInterceptor(registry, new GrpcTagProvider() {
            @Override
            public Iterable<Tag> getBaseTags(MethodDescriptor<?, ?> method) {
                return Tags.of(
                        "tacoServiceName", method.getServiceName(),
                        "tacoMethodName", method.getBareMethodName(),
                        "tacoType", method.getType().name(),
                        "tacos", "rock"
                );
            }
            @Override
            public Iterable<Tag> getTagsForResult(MethodDescriptor<?, ?> method, Status.Code code) {
                return Tags.of("tacoStatus", code.name());
            }
        });
        runARequest(metricCollectingClientInterceptor);

        Tags unexpectedTags = Tags.of(
                "method", "bare",
                "methodType", "UNARY",
                "service", "service"
        );
        assertThatThrownBy(() -> registry.get("grpc.client.responses.received").tags(unexpectedTags).counter()).isInstanceOf(MeterNotFoundException.class);

        Tags expectedTags = Tags.of(
                "tacoMethodName", "bare",
                "tacoType", "UNARY",
                "tacoServiceName", "service",
                "tacos", "rock"
        );

        assertThat( registry.get("grpc.client.responses.received").tags(expectedTags).counter().count()).isEqualTo(1.0);
        assertThat( registry.get("grpc.client.requests.sent").tags(expectedTags).counter().count()).isEqualTo(1.0);
        Timer clientTimer = registry.get("grpc.client.processing.duration").tags(expectedTags).timer();
        assertThat(clientTimer.getId().getTag("tacoStatus")).isEqualTo("OK");
        assertThat(clientTimer.getId().getTag("status")).isNull();
        assertThat( clientTimer.count()).isEqualTo(1);
    }

    private void runARequest(MetricCollectingClientInterceptor metricCollectingClientInterceptor) {
        MethodDescriptor mock = Mockito.mock(MethodDescriptor.class);
        Mockito.when(mock.getServiceName()).thenReturn("service");
        Mockito.when(mock.getBareMethodName()).thenReturn("bare");
        Mockito.when(mock.getType()).thenReturn(MethodDescriptor.MethodType.UNARY);

        AbstractMetricCollectingInterceptor.MetricSet metricSet = metricCollectingClientInterceptor.metricsFor(mock);
        metricSet.getRequestCounter().increment();
        metricSet.getResponseCounter().increment();
        Consumer<Status.Code> codeConsumer = metricSet.newProcessingDurationTiming(registry);
        codeConsumer.accept(Status.Code.OK);
    }
}
