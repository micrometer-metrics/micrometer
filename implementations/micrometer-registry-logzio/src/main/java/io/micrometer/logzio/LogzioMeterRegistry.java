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
package io.micrometer.logzio;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for Logz.io.
 */
public class LogzioMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("Logzio-metrics-publisher");

    private static final String ERROR_RESPONSE_BODY_SIGNATURE = "\"errors\":true";
    private static final Pattern STATUS_CREATED_PATTERN = Pattern.compile("\"status\":201");

    private final Logger logger = LoggerFactory.getLogger(LogzioMeterRegistry.class);

    private final LogzioConfig config;
    private final HttpSender httpClient;

    @SuppressWarnings("deprecation")
    public LogzioMeterRegistry(LogzioConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * Create a new instance with given parameters.
     *
     * @param config        configuration to use
     * @param clock         clock to use
     * @param threadFactory thread factory to use
     * @param httpClient    http client to use
     * @since 1.2.1
     */
    protected LogzioMeterRegistry(LogzioConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new LogzioNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);
    }

    @Override
    protected void publish() {
        String uri = config.uri() + "/?token=" + config.token() + "&type=micrometer_metrics";
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                String requestBody = batch.stream()
                        .map(m -> m.match(
                                this::writeGauge,
                                this::writeCounter,
                                this::writeTimer,
                                this::writeSummary,
                                this::writeLongTaskTimer,
                                this::writeTimeGauge,
                                this::writeFunctionCounter,
                                this::writeFunctionTimer,
                                this::writeMeter))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(joining("\n", "", "\n"));
                httpClient
                        .post(uri)
                        .withJsonContent(requestBody)
                        .send()
                        .onSuccess(response -> {
                            String responseBody = response.body();
                            if (responseBody.contains(ERROR_RESPONSE_BODY_SIGNATURE)) {
                                logger.debug("failed metrics payload: {}", requestBody);
                            } else {
                                logger.debug("successfully sent metrics to elastic");
                            }
                        })
                        .onError(response -> {
                            logger.debug("failed metrics payload: {}", requestBody);
                            logger.error("failed to send metrics to logzio: {}", response.body());
                        });
            } catch (Throwable e) {
                logger.error("failed to send metrics to logzio", e);
            }
        }
    }

    // VisibleForTesting
    static int countCreatedItems(String responseBody) {
        Matcher matcher = STATUS_CREATED_PATTERN.matcher(responseBody);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // VisibleForTesting
    Optional<String> writeCounter(Counter counter) {
        return writeCounter(counter, counter.count());
    }

    // VisibleForTesting
    Optional<String> writeFunctionCounter(FunctionCounter counter) {
        return writeCounter(counter, counter.count());
    }

    private Optional<String> writeCounter(Meter meter, double value) {
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(meter, builder -> {
                builder.append(":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(gauge, builder -> {
                builder.append(":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeTimeGauge(TimeGauge gauge) {
        double value = gauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(gauge, builder -> {
                builder.append(":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeFunctionTimer(FunctionTimer timer) {
        double sum = timer.totalTime(getBaseTimeUnit());
        double mean = timer.mean(getBaseTimeUnit());
        if (Double.isFinite(sum) && Double.isFinite(mean)) {
            return Optional.of(writeDocument(timer, builder -> {
                builder.append(":").append(timer.count());
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeLongTaskTimer(LongTaskTimer timer) {
        return Optional.of(writeDocument(timer, builder -> {
            builder.append(":").append(timer.activeTasks());
        }));
    }

    // VisibleForTesting
    Optional<String> writeTimer(Timer timer) {
        return Optional.of(writeDocument(timer, builder -> {
            builder.append(":").append(timer.count());
        }));
    }

    // VisibleForTesting
    Optional<String> writeSummary(DistributionSummary summary) {
        HistogramSnapshot histogramSnapshot = summary.takeSnapshot();
        return Optional.of(writeDocument(summary, builder -> {
            builder.append(":").append(histogramSnapshot.count());
        }));
    }

    // VisibleForTesting
    Optional<String> writeMeter(Meter meter) {
        Iterable<Measurement> measurements = meter.measure();
        List<String> names = new ArrayList<>();
        // Snapshot values should be used throughout this method as there are chances for values to be changed in-between.
        List<Double> values = new ArrayList<>();
        for (Measurement measurement : measurements) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            names.add(measurement.getStatistic().getTagValueRepresentation());
            values.add(value);
        }
        if (names.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(writeDocument(meter, builder -> {
            for (int i = 0; i < names.size(); i++) {
                builder.append(",\"").append(names.get(i)).append("\":\"").append(values.get(i)).append("\"");
            }
        }));
    }

    // VisibleForTesting
    String writeDocument(Meter meter, Consumer<StringBuilder> consumer) {

        StringBuilder sb = new StringBuilder();
        String name = getConventionName(meter.getId());
        String type = meter.getId().getType().toString().toLowerCase();
        sb.append("{\"metrics\":{").append("\"" + escapeJson(name)).append("." + escapeJson(type) + "\"");
        consumer.accept(sb);
        sb.append("}").append(", \"dimensions\": {");

        List<Tag> tags = getConventionTags(meter.getId());
        sb.append(tags.stream().map(tag -> "\"" + escapeJson(tag.getKey()) + "\"" + ":\"" + escapeJson(tag.getValue()) + "\"").collect(joining(", ")));
        sb.append("}}");

        return sb.toString();
    }


    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
