package io.micrometer.kairos;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.HttpHeader;
import io.micrometer.core.instrument.util.HttpMethod;
import io.micrometer.core.instrument.util.MediaType;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

/**
 * @author Anton Ilinchik
 */
public class KairosMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(KairosMeterRegistry.class);

    private final KairosConfig config;

    public KairosMeterRegistry(KairosConfig config) {
        this(config, Clock.SYSTEM);
    }

    public KairosMeterRegistry(KairosConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public KairosMeterRegistry(KairosConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        requireNonNull(config, "config must not be null");
        requireNonNull(threadFactory, "threadFactory must not be null");

        this.config = config;
        config().namingConvention(new KairosNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            long wallTime = config().clock().wallTime();
            String payload =
                batch.stream().flatMap(m -> {
                    if (m instanceof Timer) {
                        return writeTimer((Timer) m, wallTime);
                    }
                    if (m instanceof Counter) {
                        return writeCounter((Counter) m, wallTime);
                    }
                    if (m instanceof LongTaskTimer) {
                        return writeLongTaskTimer((LongTaskTimer) m, wallTime);
                    }
                    if (m instanceof Gauge) {
                        return writeGauge((Gauge) m, wallTime);
                    }
                    if (m instanceof TimeGauge) {
                        return writeTimeGauge((TimeGauge) m, wallTime);
                    }
                    if (m instanceof FunctionTimer) {
                        return writeFunctionTimer((FunctionTimer) m, wallTime);
                    }
                    if (m instanceof FunctionCounter) {
                        return writeFunctionCounter((FunctionCounter) m, wallTime);
                    }
                    if (m instanceof DistributionSummary) {
                        return writeSummary((DistributionSummary) m, wallTime);
                    }
                    return writeCustomMetric(m, wallTime);
                }).collect(Collectors.joining(", "));

            sendToKairos(String.format("[%s]", payload));
        }
    }

    private void sendToKairos(String bulkPayload) {
        HttpURLConnection con = null;

        try {
            URL templateUrl = new URL(config.host());
            con = (HttpURLConnection) templateUrl.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(HttpMethod.POST);
            con.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            con.setDoOutput(true);

            if (StringUtils.isNotBlank(config.userName()) && StringUtils.isNotBlank(config.password())) {
                byte[] authBinary = (config.userName() + ":" + config.password()).getBytes(StandardCharsets.UTF_8);
                String authEncoded = Base64.getEncoder().encodeToString(authBinary);
                con.setRequestProperty(HttpHeader.AUTHORIZATION, "Basic " + authEncoded);
            }
            logger.trace("Sending payload to KairosDB:");
            logger.trace(bulkPayload);

            try (OutputStream os = con.getOutputStream()) {
                os.write(bulkPayload.getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                logger.trace("successfully sent events to KairosDB");
            } else {
                logger.error("failed to send metrics, status: {} message: {}", status, con.getResponseMessage());
            }
        } catch (Throwable e) {
            logger.warn("failed to send metrics", e);
        } finally {
            quietlyCloseUrlConnection(con);
        }
    }

    private void quietlyCloseUrlConnection(@Nullable HttpURLConnection con) {
        try {
            if (con != null) {
                con.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    private static class KairosMetricBuilder {
        private StringBuilder sb = new StringBuilder("{");

        KairosMetricBuilder field(String key, String value) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
            return this;
        }

        KairosMetricBuilder datapoints(Long wallTime, Number value) {
            sb.append(",\"datapoints\":[[").append(wallTime).append(",").append(value).append("]]");
            return this;
        }

        KairosMetricBuilder tags(List<Tag> tags) {
            KairosMetricBuilder tagBuilder = new KairosMetricBuilder();
            if (tags.isEmpty()) {
                // tags field is required for KairosDB, use hostname as a default tag
                try {
                    tagBuilder.field("hostname", InetAddress.getLocalHost().getHostName());
                } catch (UnknownHostException ignore) {
                    /* ignore */
                }
            } else {
                for (Tag tag : tags) {
                    tagBuilder.field(tag.getKey(), tag.getValue());
                }
            }

            sb.append(",\"tags\":").append(tagBuilder.build());
            return this;
        }

        String build() {
            return sb.append("}").toString();
        }
    }

    Stream<String> writeSummary(DistributionSummary summary, Long wallTime) {
        return Stream.of(
            writeMetric(idWithSuffix(summary.getId(), "count"), wallTime, summary.count()),
            writeMetric(idWithSuffix(summary.getId(), "mean"), wallTime, summary.mean()),
            writeMetric(idWithSuffix(summary.getId(), "sum"), wallTime, summary.totalAmount()),
            writeMetric(idWithSuffix(summary.getId(), "max"), wallTime, summary.max())
        );
    }

    Stream<String> writeFunctionTimer(FunctionTimer timer, Long wallTime) {
        return Stream.of(
            writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
            writeMetric(idWithSuffix(timer.getId(), "mean"), wallTime, timer.mean(getBaseTimeUnit())),
            writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    Stream<String> writeTimer(Timer timer, Long wallTime) {
        return Stream.of(
            writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
            writeMetric(idWithSuffix(timer.getId(), "max"), wallTime, timer.max(getBaseTimeUnit())),
            writeMetric(idWithSuffix(timer.getId(), "mean"), wallTime, timer.mean(getBaseTimeUnit())),
            writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    Stream<String> writeFunctionCounter(FunctionCounter counter, Long wallTime) {
        return Stream.of(writeMetric(counter.getId(), wallTime, counter.count()));
    }

    Stream<String> writeCounter(Counter counter, Long wallTime) {
        return Stream.of(writeMetric(counter.getId(), wallTime, counter.count()));
    }

    Stream<String> writeGauge(Gauge gauge, Long wallTime) {
        Double value = gauge.value();
        return value.isNaN() ? Stream.empty() : Stream.of(writeMetric(gauge.getId(), wallTime, value));
    }

    Stream<String> writeTimeGauge(TimeGauge timeGauge, Long wallTime) {
        Double value = timeGauge.value(getBaseTimeUnit());
        return value.isNaN() ? Stream.empty() : Stream.of(writeMetric(timeGauge.getId(), wallTime, value));
    }

    Stream<String> writeLongTaskTimer(LongTaskTimer timer, long wallTime) {
        return Stream.of(
            writeMetric(idWithSuffix(timer.getId(), "activeTasks"), wallTime, timer.activeTasks()),
            writeMetric(idWithSuffix(timer.getId(), "duration"), wallTime, timer.duration(getBaseTimeUnit()))
        );
    }

    private Stream<String> writeCustomMetric(final Meter meter, Long wallTime) {
        return StreamSupport.stream(meter.measure().spliterator(), false)
                            .map(ms -> new KairosMetricBuilder()
                                .field("name", ms.getStatistic().getTagValueRepresentation())
                                .datapoints(wallTime, ms.getValue())
                                .tags(getConventionTags(meter.getId()))
                                .build());
    }

    String writeMetric(Meter.Id id, Long wallTime, Number value) {
        return new KairosMetricBuilder()
            .field("name", getConventionName(id))
            .datapoints(wallTime, value)
            .tags(getConventionTags(id))
            .build();
    }

    private Meter.Id idWithSuffix(final Meter.Id id, final String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
