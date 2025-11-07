/*
 * Copyright 2025 the original author or authors.
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
package io.micrometer.stackdriver;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.ListMetricDescriptorsRequest;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StackdriverMeterRegistryDescriptorAutoCreationTest {

    @Test
    void disabledAutoCreationShouldNotCreateMetricDescriptor() {
        var mockClient = mock(MetricServiceClient.class);
        when(mockClient.listMetricDescriptors((ListMetricDescriptorsRequest) any())).thenReturn(emptyResponse());

        var meterRegistry = mockMeterRegistry(mockClient, false);

        meterRegistry.start(Executors.defaultThreadFactory());

        meterRegistry.counter("testCounter", Tags.empty());

        meterRegistry.publish();

        verify(mockClient, never()).createMetricDescriptor(any());
        verify(mockClient, times(1)).createTimeSeries(any());

        meterRegistry.close();
    }

    @Test
    void enabledAutoCreationShouldCreateMetricDescriptor() {
        var mockClient = mock(MetricServiceClient.class);
        var meterRegistry = mockMeterRegistry(mockClient, true);

        meterRegistry.start(Executors.defaultThreadFactory());

        meterRegistry.counter("testCounter", Tags.empty());

        meterRegistry.publish();

        verify(mockClient, times(1)).createMetricDescriptor(any());
        verify(mockClient, times(1)).createTimeSeries(any());

        meterRegistry.close();
    }

    private StackdriverMeterRegistry mockMeterRegistry(MetricServiceClient mockClient,
            boolean autoCreateMetricDescriptors) {
        var factory = new MetricServiceClientFactory() {
            @Override
            public MetricServiceClient create(MetricServiceSettings settings) {
                return mockClient;
            }
        };

        return StackdriverMeterRegistry.builder(stackdriverConfig(autoCreateMetricDescriptors))
            .clientFactory(factory)
            .build();
    }

    private StackdriverConfig stackdriverConfig(boolean autoCreateMetricDescriptors) {
        return new StackdriverConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String projectId() {
                return "doesnotmatter";
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public boolean autoCreateMetricDescriptors() {
                return autoCreateMetricDescriptors;
            }
        };
    }

    private MetricServiceClient.ListMetricDescriptorsPagedResponse emptyResponse() {
        return mock(MetricServiceClient.ListMetricDescriptorsPagedResponse.class);
    }

}
