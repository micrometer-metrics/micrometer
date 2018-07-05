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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.HttpHeader;
import io.micrometer.core.instrument.util.HttpMethod;
import io.micrometer.core.instrument.util.IOUtils;
import io.micrometer.core.instrument.util.MediaType;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Publishes metrics to AppOptics.
 *
 * @author Hunter Sherman
 */
public class AppOpticsMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(AppOpticsMeterRegistry.class);

    private final AppOpticsConfig config;
    private final String prefix;

    public AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        this.config = config;
        if(!config.metricPrefix().isEmpty() && !config.metricPrefix().endsWith(".")) {
            this.prefix = config.metricPrefix() + ".";
        } else {
            this.prefix = config.metricPrefix();
        }
        if(config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {

        try {
            final URL endpoint = URI.create(config.uri()).toURL();

            final AppOpticsDto dto = AppOpticsDto.newBuilder()
                .withTime(System.currentTimeMillis() / 1000)
                .withPeriod((int) config.step().getSeconds())
                .withTag("source", config.source())
                .build();

            getMeters().forEach(meter -> addMeter(meter, dto));

            if(dto.getMeasurements().isEmpty()) {
                logger.debug("No metrics to send.");
                return;
            }

            dto.batch(config.batchSize()).forEach(it -> sendMeasurements(endpoint, it));

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed AppOptics endpoint -- see the 'uri' configuration", e);
        } catch (Throwable t) {
            logger.warn("Failed to send metrics to AppOptics", t);
        }
    }

    private void addMeter(Meter meter, AppOpticsDto dto) {

        if (meter instanceof Timer) {
            dto.addMeasurement(fromTimer((Timer) meter));
        } else if (meter instanceof FunctionTimer) {
            dto.addMeasurement(fromFunctionTimer((FunctionTimer) meter));
        } else if (meter instanceof DistributionSummary) {
            dto.addMeasurement(fromDistributionSummary((DistributionSummary) meter));
        } else if (meter instanceof TimeGauge) {
            final Measurement measurement = fromTimeGauge((TimeGauge) meter);
            if(null != measurement) dto.addMeasurement(measurement);
        } else if (meter instanceof Gauge) {
            final Measurement measurement = fromGauge((Gauge) meter);
            if(null != measurement) dto.addMeasurement(measurement);
        } else if (meter instanceof Counter) {
            dto.addMeasurement(fromCounter((Counter) meter));
        } else if (meter instanceof FunctionCounter) {
            dto.addMeasurement(fromFunctionCounter((FunctionCounter) meter));
        } else if (meter instanceof LongTaskTimer) {
            dto.addMeasurement(fromLongTaskTimer((LongTaskTimer) meter));
        } else {
            dto.addMeasurements(fromMeter(meter));
        }
    }

    protected AggregateMeasurement fromTimer(Timer timer) {

        return AggregateMeasurement.newBuilder()
            .withName(addPrefix(timer.getId().getName()))
            .withSum(timer.totalTime(getBaseTimeUnit()))
            .withCount(timer.count())
            .withMax(timer.max(getBaseTimeUnit()))
            .withTags(timer.getId().getTags())
            .build();
    }

    protected AggregateMeasurement fromFunctionTimer(FunctionTimer timer) {

        return AggregateMeasurement.newBuilder()
            .withName(addPrefix(timer.getId().getName()))
            .withSum(timer.totalTime(getBaseTimeUnit()))
            .withCount((long) timer.count())
            .withTags(timer.getId().getTags())
            .build();
    }

    protected AggregateMeasurement fromLongTaskTimer(LongTaskTimer timer) {

        return AggregateMeasurement.newBuilder()
            .withName(addPrefix(timer.getId().getName()))
            .withSum(timer.duration(getBaseTimeUnit()))
            .withCount((long)timer.activeTasks())
            .withTags(timer.getId().getTags())
            .build();
    }

    protected AggregateMeasurement fromDistributionSummary(DistributionSummary summary) {

        return AggregateMeasurement.newBuilder()
            .withName(addPrefix(summary.getId().getName()))
            .withSum(summary.totalAmount())
            .withCount(summary.count())
            .withMax(summary.max())
            .withTags(summary.getId().getTags())
            .build();
    }

    @Nullable
    protected SingleMeasurement fromTimeGauge(TimeGauge gauge) {

        final Double val = gauge.value(getBaseTimeUnit());

        if(val.isNaN()) return null;
        return SingleMeasurement.newBuilder()
            .withName(
                addPrefix(gauge.getId().getName()))
            .withValue(val)
            .withTags(gauge.getId().getTags())
            .build();
    }

    @Nullable
    protected SingleMeasurement fromGauge(Gauge gauge) {

        final Double val = gauge.value();

        if(val.isNaN()) return null;
        return SingleMeasurement.newBuilder()
            .withName(
                addPrefix(gauge.getId().getName()))
            .withValue(val)
            .withTags(gauge.getId().getTags())
            .build();
    }

    protected SingleMeasurement fromCounter(Counter counter) {

        return SingleMeasurement.newBuilder()
            .withName(
                addPrefix(counter.getId().getName()))
            .withValue(counter.count())
            .withTags(counter.getId().getTags())
            .build();
    }

    protected SingleMeasurement fromFunctionCounter(FunctionCounter counter) {

        return SingleMeasurement.newBuilder()
            .withName(
                addPrefix(counter.getId().getName()))
            .withValue(counter.count())
            .withTags(counter.getId().getTags())
            .build();
    }

    protected Stream<Measurement> fromMeter(Meter meter) {

        return StreamSupport.stream(meter.measure().spliterator(), false)
                    .map(stat -> SingleMeasurement.newBuilder()
                            .withName(addPrefix(stat.getStatistic().getTagValueRepresentation()))
                            .withValue(stat.getValue())
                            .withTags(meter.getId().getTags())
                            .build());
    }

    private String addPrefix(String metricName) {

        return prefix + metricName;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private void sendMeasurements(URL endpoint, AppOpticsDto dto) {

        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) endpoint.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(HttpMethod.POST);
            con.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            con.setRequestProperty(HttpHeader.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(
                config.token().concat(":").getBytes(Charset.forName("UTF-8"))));

            con.setDoOutput(true);

            final String body = dto.toJson();

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                logger.info("Successfully sent {} measurements to AppOptics", dto.getMeasurements().size());
            } else if (status >= 400) {
                if (logger.isErrorEnabled()) {
                    logger.error("failed to send metrics: {}", IOUtils.toString(con.getErrorStream()));
                }
            } else {
                logger.error("failed to send metrics: http {}", status);
            }

        } catch (Throwable e) {
            logger.warn("failed to send metrics", e);
        } finally {
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignore) {
            }
        }
    }
}
