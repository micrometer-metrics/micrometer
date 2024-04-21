/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("docker")
class CloudWatchIntegrationTest {

    private static final SdkAsyncHttpClient.Builder<?> AWS_SDK_HTTP_CLIENT_BUILDER = NettyNioAsyncHttpClient.builder();

    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.3.0"))
        .withServices(LocalStackContainer.Service.CLOUDWATCH);

    private static final AwsCredentialsProvider AWS_CREDENTIALS_PROVIDER = StaticCredentialsProvider
        .create(AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));

    private static final Region AWS_REGION = Region.of(LOCALSTACK.getRegion());

    private String meterName;

    private final CloudWatchConfig config = new CloudWatchConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String namespace() {
            return "namespace";
        }

        @Override
        @SuppressWarnings("deprecation")
        public Duration readTimeout() {
            return Duration.ofSeconds(1);
        }
    };

    private MockClock clock;

    private CloudWatchMeterRegistry registry;

    private CloudWatchAsyncClient cloudWatchAsyncClient;

    @BeforeEach
    void beforeEachTest() {
        clock = new MockClock();
        meterName = UUID.randomUUID().toString();
        cloudWatchAsyncClient = spy(CloudWatchAsyncClient.builder()
            .httpClient(AWS_SDK_HTTP_CLIENT_BUILDER.build())
            .credentialsProvider(AWS_CREDENTIALS_PROVIDER)
            .region(AWS_REGION)
            .build());
        registry = new CloudWatchMeterRegistry(config, clock, cloudWatchAsyncClient);
    }

    @Test
    void batchSizeShouldWorkOnMetricDatum() throws Exception {
        for (int i = 0; i < CloudWatchConfig.MAX_BATCH_SIZE; i++) {
            Timer.builder("timer." + i).register(this.registry);
        }
        assertThat(this.registry.metricData()).hasSize(2000);
        this.registry.publish();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<PutMetricDataRequest> argumentCaptor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(this.cloudWatchAsyncClient, times(2)).putMetricData(argumentCaptor.capture());
        List<PutMetricDataRequest> allValues = argumentCaptor.getAllValues();
        assertThat(allValues.get(0).metricData()).hasSize(CloudWatchConfig.MAX_BATCH_SIZE);
        assertThat(allValues.get(1).metricData()).hasSize(CloudWatchConfig.MAX_BATCH_SIZE);
    }

    @Test
    void putMetricDataShouldBeCalledOnPublish() {
        registry.counter(meterName).increment();
        registry.publish();

        verify(cloudWatchAsyncClient, times(1)).putMetricData(isA(PutMetricDataRequest.class));
    }

    @Test
    void emptyRegistryDoesNotPublishToAws() {
        assertThat(registry.metricData()).isEmpty();
        registry.publish();
        verifyNoInteractions(cloudWatchAsyncClient);
    }

}
