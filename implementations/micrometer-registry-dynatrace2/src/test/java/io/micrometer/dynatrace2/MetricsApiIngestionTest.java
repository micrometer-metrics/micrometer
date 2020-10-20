package io.micrometer.dynatrace2;

import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpSender.Method;
import io.micrometer.core.ipc.http.HttpSender.Request;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import wiremock.com.google.common.collect.ImmutableMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.micrometer.dynatrace2.MetricsApiIngestion.METRICS_INGESTION_URL;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricsApiIngestionTest implements WithAssertions {

    HttpSender httpSender;
    DynatraceConfig config;
    MetricsApiIngestion metricsApiIngestion;

    @BeforeEach
    void setUp() throws Throwable {
        httpSender = spy(HttpSender.class);
        when(httpSender.send(any())).thenReturn(new HttpSender.Response(202, null));

        config = mock(DynatraceConfig.class);
        when(config.batchSize()).thenReturn(2);
        when(config.uri()).thenReturn("https://micrometer.dynatrace.com");
        when(config.apiToken()).thenReturn("my-token");

        metricsApiIngestion = new MetricsApiIngestion(httpSender, config);
    }

    @Test
    void shouldNotSendRequests_whenNoMetricLines() {
        List<String> metricLines = emptyList();

        metricsApiIngestion.sendInBatches(metricLines);

        verifyNoInteractions(httpSender);
    }

    @Test
    void shouldSendOneRequest_whenLessOrEqualToBatchSize() {
        List<String> metricLines = asList("first", "second");

        metricsApiIngestion.sendInBatches(metricLines);

        verify(httpSender).post(any());
    }

    @Test
    void shouldSendMultipleRequests_whenGreaterThanBatchSize() {
        List<String> metricLines = IntStream.range(0, 20)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        metricsApiIngestion.sendInBatches(metricLines);

        verify(httpSender, times(10)).post(any());
    }

    @Test
    void shouldFulfillApiSpec() throws Throwable {
        List<String> metricLines = asList("first", "second");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);

        metricsApiIngestion.sendInBatches(metricLines);
        verify(httpSender).send(requestCaptor.capture());

        assertThat(requestCaptor.getValue())
                .extracting(
                        Request::getMethod,
                        r -> r.getUrl().toString(),
                        Request::getRequestHeaders,
                        r -> new String(r.getEntity(), StandardCharsets.UTF_8))
                .contains(
                        Method.POST,
                        config.uri() + METRICS_INGESTION_URL,
                        ImmutableMap.of(
                                "Authorization", "Api-Token " + config.apiToken(),
                                "Content-Type", "text/plain"),
                        "first" + System.lineSeparator() + "second");
    }

    @Test
    void shouldKeepSendingTheRequests_whenOneHttpRequestThrowsException() throws Throwable {
        when(httpSender.send(any()))
                .thenThrow(new IllegalArgumentException())
                .thenReturn(new HttpSender.Response(202, null));

        List<String> metricLines = asList("first", "second", "third", "fourth");

        metricsApiIngestion.sendInBatches(metricLines);

        verify(httpSender, times(2)).post(any());
    }
}
