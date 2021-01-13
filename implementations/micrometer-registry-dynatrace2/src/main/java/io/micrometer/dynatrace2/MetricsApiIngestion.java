/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.util.AbstractPartition;
import io.micrometer.core.ipc.http.HttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

class MetricsApiIngestion {
    public static final String METRICS_INGESTION_URL = "/api/v2/metrics/ingest";

    private final Logger logger = LoggerFactory.getLogger(MetricsApiIngestion.class);
    private final HttpSender httpSender;
    private final DynatraceConfig config;

    MetricsApiIngestion(HttpSender httpSender, DynatraceConfig config) {
        this.httpSender = httpSender;
        this.config = config;
    }

    void sendInBatches(List<String> metricLines) {
        MetricLinePartition.partition(metricLines, config.batchSize())
                .forEach(this::send);
    }

    private void send(List<String> metricLines) {
        try {
            String body = metricLines.stream().collect(Collectors.joining(System.lineSeparator()));

            httpSender.post(config.uri() + METRICS_INGESTION_URL)
                    .withHeader("Authorization", "Api-Token " + config.apiToken())
                    .withPlainText(body)
                    .send()
                    .onSuccess((r) -> logger.debug("Ingested {} metric lines into Dynatrace", metricLines.size()))
                    .onError((r) -> logger.error("Failed metric ingestion. code={} body={}", r.code(), r.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion", throwable);
        }
    }

    static class MetricLinePartition extends AbstractPartition<String> {

        MetricLinePartition(List<String> list, int partitionSize) {
            super(list, partitionSize);
        }

        static List<List<String>> partition(List<String> list, int partitionSize) {
            return new MetricLinePartition(list, partitionSize);
        }
    }
}
