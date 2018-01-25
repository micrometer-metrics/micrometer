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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.net.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

public class WavefrontMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final String wavefrontProxyHost;
    private final String wavefrontProxyPort;
    private int publishCounter;

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;
        this.publishCounter = 0;

        try {
            this.wavefrontProxyHost = config.getHost();
            this.wavefrontProxyPort = config.getPort();
        } catch (Exception e) {
            // not possible
            throw new RuntimeException(e);
        }

        config().namingConvention(new WavefrontNamingConvention());

        if(config.enabled()) {
            logger.debug("[registry]publish interval set to: " + config.step().getSeconds() + " seconds.");
            start(threadFactory);
            logger.debug("[registry]step thread started.");
        }
    }

    @Override
    protected void publish()
    {
        publishCounter++;
        logger.debug("[publish][" + publishCounter + "]start");
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {

                logger.debug("[publish]batch.size() = " + batch.size());

                Socket socket = null;
                OutputStreamWriter writer = null;

                try {
                    if(config.test() == false) {
                        socket = new Socket(wavefrontProxyHost, Integer.parseInt(wavefrontProxyPort));
                        writer = new OutputStreamWriter(socket.getOutputStream());
                        logger.debug("[publish]connectionEstablished to " + wavefrontProxyHost + ":" + wavefrontProxyPort);
                    }
                    else
                    {
                        logger.debug("[publish]testing mode on - output will be redirected to info log");
                    }

                    // now the writer is ready to send the metrics.
                    String body =
                        batch.stream().flatMap(m -> {
                            if (m instanceof Timer) {
                                return writeTimer((Timer) m);
                            }
                            if (m instanceof DistributionSummary) {
                                return writeSummary((DistributionSummary) m);
                            }
                            if (m instanceof FunctionTimer) {
                                return writeTimer((FunctionTimer) m);
                            }
                            return writeMeter(m);
                        }).collect(joining("\n"));

                    // write the collected metric data to output stream
                    if(config.test() == false) {
                        logger.debug(body);
                        writer.write(body);
                        writer.write("\n");
                    }
                    else
                    {
                        logger.info(body);
                    }
                }
                finally {
                    if(config.test() == false) {
                        logger.debug("[publish]closing connection.");
                        writer.flush();
                        quietlyCloseSocketConnection(socket);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }
        logger.debug("[publish][" + publishCounter + "]end");
    }

    private void quietlyCloseSocketConnection(@Nullable Socket con) {
        try {
            if (con != null) {
                con.close();
            }
        } catch (Exception ignore) {
            logger.debug(ignore.getMessage(), ignore);
        }
    }

    private Stream<String> writeTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
            writeMetric(id, "count", wallTime, timer.count()),
            writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())),
            writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<String> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max(getBaseTimeUnit())));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value(getBaseTimeUnit())));
        }

        // FIXME write histogram format here
        for (CountAtValue countAtValue : snapshot.histogramCounts()) {
            // countAtValue represents a single histogram bucket
        }

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total()));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean()));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max()));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value()));
        }

        // FIXME write histogram format here
        for (CountAtValue countAtValue : snapshot.histogramCounts()) {
            // countAtValue represents a single histogram bucket
        }

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter m) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
            .map(ms -> {
                Meter.Id id = m.getId().withTag(ms.getStatistic());
                return writeMetric(id, null, wallTime, ms.getValue());
            });
    }

    String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        return writeMetric(id, suffix,null, wallTime, value);
    }

    /**
     * https://docs.wavefront.com/wavefront_data_format.html#wavefront-data-format-syntax
     */
    String writeMetric(Meter.Id id, @Nullable String suffix, @Nullable String source, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);
        wallTime = wallTime / 1000;         // we need to convert ms to s
        if(source == null)
        {
            // get current system's hostname
            try {
                source = InetAddress.getLocalHost().getHostName();
            }
            catch(UnknownHostException uhe)
            {
                source = "unknown";
                logger.warn("[writeMetric]could not determine hostname to use as source..", uhe);
            }
        }
        if(source != null)
        {
            return getConventionName(id) + " " + DoubleFormat.toString(value) + " " + wallTime + " source=" + source + " " +
                getConventionTags(fullId)
                    .stream()
                    .map(t -> t.getKey() + "=\"" + t.getValue() + "\"")
                    .collect(joining(" "));
        }
        else
            return null;
    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        // TODO what is the typical base unit of time?
        return TimeUnit.SECONDS;
    }
}
