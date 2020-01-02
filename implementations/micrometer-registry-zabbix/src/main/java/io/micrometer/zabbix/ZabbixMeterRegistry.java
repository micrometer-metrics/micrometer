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
package io.micrometer.zabbix;

import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.SenderResult;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * {@link MeterRegistry} for Zabbix.
 *
 * @see <a href="https://www.zabbix.com/">Zabbix</a>
 * @since 1.4.0
 */
public class ZabbixMeterRegistry extends StepMeterRegistry {

    public static final String KEY_SUFFIX_EMPTY = "";
    public static final String KEY_SUFFIX_VALUE = "value";
    public static final String KEY_SUFFIX_AVG = "avg";
    public static final String KEY_SUFFIX_SUM = "sum";
    public static final String KEY_SUFFIX_COUNT = "count";
    public static final String KEY_SUFFIX_MAX = "max";
    public static final String KEY_SUFFIX_DURATION = "duration";
    public static final String KEY_SUFFIX_ACTIVE_TASKS = "activeTasks";
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("zabbix-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(ZabbixMeterRegistry.class);

    private final ZabbixConfig config;
    private final ZabbixSender zabbixSender;
    private final KeyNameGenerator keyNameGenerator;
    private final ZabbixDiscoveryProvider discoveryProvider;
    private final ZabbixDiscoveryPublisher discoveryPublisher;

    @SuppressWarnings("deprecation")
    public ZabbixMeterRegistry(ZabbixConfig config, Clock clock, final ZabbixDiscoveryProvider discoveryProvider) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new ZabbixSender(config.instanceHost(), config.instancePort(), Math.toIntExact(config.connectTimeout().toMillis()),
                        Math.toIntExact(config.readTimeout().toMillis())),
                new ZabbixKeyNameGenerator(), discoveryProvider);
    }

    private ZabbixMeterRegistry(ZabbixConfig config, Clock clock, ThreadFactory threadFactory,
                                ZabbixSender zabbixSender, KeyNameGenerator keyNameGenerator,
                                final ZabbixDiscoveryProvider discoveryProvider) {
        super(config, clock);
        this.discoveryProvider = discoveryProvider;

        config().namingConvention(NamingConvention.dot);

        this.config = config;
        this.zabbixSender = zabbixSender;
        this.keyNameGenerator = keyNameGenerator;

        this.discoveryPublisher = new ZabbixDiscoveryPublisher(zabbixSender, config);

        start(threadFactory);
    }

    @Override
    public void start(final ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("Publishing metrics to Zabbix at {}:{} every {}",
                    config.instanceHost(), config.instancePort(), TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        long clockInS = clock.wallTime() / 1000;

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                List<DataObject> requestBody = batch.stream()
                        .flatMap(meter -> meter.match(g -> writeGauge(g, clockInS), m -> counterData(m, clockInS),
                                t -> timerData(t, clockInS), s -> summaryData(s, clockInS),
                                t -> longTaskTimerData(t, clockInS), g -> timeGaugeData(g, clockInS),
                                c -> functionCounterData(c, clockInS), t -> functionTimerData(t, clockInS),
                                m -> meterData(m, clockInS)))
                        .filter(Objects::nonNull)
                        .map(discoveryProvider::visit)
                        .map(item -> DataObject.builder()
                                .host(item.getHost())
                                .key(item.getKey())
                                .value(DoubleFormat.decimalOrNan(item.getValue()))
                                .clock(item.getClock())
                                .build())
                        .collect(Collectors.toList());

                discoveryPublisher.publish(discoveryProvider.takeItems());

                SenderResult senderResult = zabbixSender.send(requestBody, clockInS);
                if (!senderResult.success()) {
                    logger.debug("failed metrics payload: {}", requestBody);
                    logger.error(
                            "failed to send metrics to Zabbix @ {}:{} (sent {} metrics but created {} metrics): {}",
                            config.instanceHost(), config.instancePort(), senderResult.getTotal(), senderResult.getProcessed(),
                            senderResult);
                } else {
                    logger.debug("successfully sent {} metrics to Zabbix", senderResult.getTotal());
                }
            } catch (Throwable e) {
                logger.error("failed to send metrics to Zabbix", e);
            }
        }
    }

    private Stream<ZabbixDataItem> writeGauge(Gauge gauge, long wallTime) {
        return Stream.of(zabbixDataItem(gauge.getId(), KEY_SUFFIX_SUM, gauge.value(), wallTime));
    }

    private Stream<ZabbixDataItem> counterData(Counter counter, final long wallTime) {
        return Stream.of(zabbixDataItem(counter.getId(), KEY_SUFFIX_VALUE, counter.count(), wallTime));
    }

    private Stream<ZabbixDataItem> timerData(final Timer timer, final long wallTime) {
        final Stream.Builder<ZabbixDataItem> metrics = Stream.builder();
        Meter.Id id = timer.getId();
        metrics.add(zabbixDataItem(id, KEY_SUFFIX_SUM, timer.totalTime(getBaseTimeUnit()), wallTime));
        long count = timer.count();
        metrics.add(zabbixDataItem(id, KEY_SUFFIX_COUNT, count, wallTime));
        if (count > 0) {
            metrics.add(zabbixDataItem(id, KEY_SUFFIX_AVG, timer.mean(getBaseTimeUnit()), wallTime));
            metrics.add(zabbixDataItem(id, KEY_SUFFIX_MAX, timer.max(getBaseTimeUnit()), wallTime));
        }
        return metrics.build();
    }

    private Stream<ZabbixDataItem> summaryData(final DistributionSummary summary, final long wallTime) {
        final Stream.Builder<ZabbixDataItem> metrics = Stream.builder();
        Meter.Id id = summary.getId();
        metrics.add(zabbixDataItem(id, KEY_SUFFIX_SUM, summary.totalAmount(), wallTime));
        long count = summary.count();
        metrics.add(zabbixDataItem(id, KEY_SUFFIX_COUNT, count, wallTime));
        if (count > 0) {
            metrics.add(zabbixDataItem(id, KEY_SUFFIX_AVG, summary.mean(), wallTime));
            metrics.add(zabbixDataItem(id, KEY_SUFFIX_MAX, summary.max(), wallTime));
        }

        return metrics.build();
    }

    private Stream<ZabbixDataItem> longTaskTimerData(final LongTaskTimer longTaskTimer, final long wallTime) {
        Meter.Id id = longTaskTimer.getId();
        return Stream.of(zabbixDataItem(id, KEY_SUFFIX_ACTIVE_TASKS, longTaskTimer.activeTasks(), wallTime),
                zabbixDataItem(id, KEY_SUFFIX_DURATION, longTaskTimer.duration(getBaseTimeUnit()), wallTime));
    }

    private Stream<ZabbixDataItem> timeGaugeData(TimeGauge gauge, final long wallTime) {
        return Stream.of(zabbixDataItem(gauge.getId(), KEY_SUFFIX_VALUE, gauge.value(getBaseTimeUnit()), wallTime));
    }

    private Stream<ZabbixDataItem> functionCounterData(final FunctionCounter counter, final long wallTime) {
        return Stream.of(zabbixDataItem(counter.getId(), KEY_SUFFIX_COUNT, counter.count(), wallTime));
    }

    private Stream<ZabbixDataItem> functionTimerData(final FunctionTimer timer, final long wallTime) {
        Stream.Builder<ZabbixDataItem> metrics = Stream.builder();
        Meter.Id id = timer.getId();
        double count = timer.count();
        metrics.add(zabbixDataItem(id, KEY_SUFFIX_COUNT, count, wallTime));
        if (count > 0) {
            metrics.add(zabbixDataItem(id, KEY_SUFFIX_AVG, timer.mean(getBaseTimeUnit()), wallTime));
        }
        return metrics.build();
    }

    private Stream<ZabbixDataItem> meterData(final Meter m, final long wallTime) {
        return stream(m.measure().spliterator(), false).map(ms -> {
            Meter.Id id = m.getId().withTag(ms.getStatistic());
            return zabbixDataItem(id, KEY_SUFFIX_EMPTY, ms.getValue(), wallTime);
        });
    }

    private ZabbixDataItem zabbixDataItem(Meter.Id id, String keySuffix, double value, long clock) {
        if (Double.isNaN(value)) {
            logger.trace("received nan for {}", id);
            return null;
        }

        String metricName = getConventionName(id);
        Iterable<Tag> tags = getConventionTags(id);

        return ZabbixDataItem.builder()
                .host(config.host())
                .metricName(metricName)
                .tags(tags)
                .id(id)
                .keySuffix(keySuffix)
                .key(keyNameGenerator.getKeyName(config.namePrefix(), metricName, keySuffix, tags, config.nameSuffix()))
                .clock(clock)
                .value(value)
                .build();
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

}
