/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.influx2;

import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.influx.InfluxNamingConvention;
import io.micrometer.influx.internal.LineProtocolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for InfluxDB 2.
 *
 * @author Jakub Bednar
 */
public class Influx2MeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("influx2-metrics-publisher");
    private final Influx2Config config;
    private final LineProtocolBuilder lineProtocolBuilder;
    private final HttpSender httpClient;
    private final Logger logger = LoggerFactory.getLogger(Influx2MeterRegistry.class);
    private boolean bucketExists = false;

    @SuppressWarnings("deprecation")
    public Influx2MeterRegistry(Influx2Config config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(Influx2Config)} instead.
     */
    @Deprecated
    public Influx2MeterRegistry(Influx2Config config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private Influx2MeterRegistry(Influx2Config config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new InfluxNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);

        lineProtocolBuilder = new LineProtocolBuilder(getBaseTimeUnit(), config());
    }

    public static Builder builder(Influx2Config config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to influx every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    private void createBucketIfNecessary() {
        if (!config.autoCreateBucket() || bucketExists)
            return;

        try {
            String createBucketJSON = new CreateBucketJSONBuilder(config.bucket(), config.org())
                    .setEverySeconds(config.everySeconds())
                    .build();

            httpClient
                    .post(config.uri() + "/bucket")
                    .withHeader("Authorization", "Token " + config.token())
                    .withJsonContent(createBucketJSON)
                    .send()
                    .onSuccess(response -> {
                        logger.debug("influx bucket {} is ready to receive metrics", config.bucket());
                        bucketExists = true;
                    })
                    .onError(response -> logger.error("unable to create bucket '{}': {}", config.bucket(), response.body()));
        } catch (Throwable e) {
            logger.warn("unable to create bucket '{}'", config.bucket(), e);
        }
    }

    @Override
    protected void publish() {
        createBucketIfNecessary();

        try {
            String influxEndpoint = config.uri() + "/write?&precision=ms&bucket=" + config.bucket() + "&org=" + config.org();

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                httpClient.post(influxEndpoint)
                        .withHeader("Authorization", "Token " + config.token())
                        .withPlainText(batch.stream()
                                .flatMap(m -> m.match(
                                        lineProtocolBuilder::writeGauge,
                                        lineProtocolBuilder::writeCounter,
                                        lineProtocolBuilder::writeTimer,
                                        lineProtocolBuilder::writeSummary,
                                        lineProtocolBuilder::writeLongTaskTimer,
                                        lineProtocolBuilder::writeTimedGauge,
                                        lineProtocolBuilder::writeFunctionCounter,
                                        lineProtocolBuilder::writeFunctionTimer,
                                        lineProtocolBuilder::writeMeter))
                                .collect(joining("\n")))
                        .compressWhen(config::compressed)
                        .send()
                        .onSuccess(response -> {
                            logger.debug("successfully sent {} metrics to InfluxDB.", batch.size());
                            bucketExists = true;
                        })
                        .onError(response -> logger.error("failed to send metrics to influx: {}", response.body()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable e) {
            logger.error("failed to send metrics to influx", e);
        }
    }

    @Override
    protected final TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final Influx2Config config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(Influx2Config config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Influx2MeterRegistry build() {
            return new Influx2MeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    class Field {
        final String key;
        final double value;

        Field(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }

}
