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
package io.micrometer.core.instrument.binder.system;

import com.sun.jna.Platform;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.util.ExecutingCommand;
import oshi.util.ParseUtil;

import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.micrometer.core.instrument.binder.system.OshiProcessorMetrics.ProcessorMetric.*;

/**
 * Collects CPU (percent and/or time total) and system load (1m, 15m, 15m) from host.
 *
 * Time total is most useful for i.e prometheus while percent might be more useful for i.e
 * influxdb or elastic.
 *
 * @author Johan Rask (jrask)
 */
class OshiProcessorMetrics implements MeterBinder {

    protected static final Logger LOG = LoggerFactory.getLogger(OshiProcessorMetrics.class);


    public static final int MIN_REFRESH_INTERVAL = 2000;


    public enum CpuSampleType {
        ALL,
        SECONDS_TOTAL,
        PERCENT
    }

    protected enum ProcessorMetric {
        USER_PERCENT,
        NICE_PERCENT,
        SYSTEM_PERCENT,
        IDLE_PERCENT,
        IOWAIT_PERCENT,
        IRQ_PERCENT,
        SOFTIRQ_PERCENT,
        STEAL_PERCENT,

        LOAD_1M,
        LOAD_5M,
        LOAD_15M,

        USER_TIME_SECONDS_TOTAL,
        NICE_TIME_SECONDS_TOTAL,
        SYS_TIME_SECONDS_TOTAL,
        IDLE_TIME_SECONDS_TOTAL,
        IOWAIT_TIME_IN_SECONDS_TOTAL,
        IRQ_TIME_IN_SECONDS_TOTAL,
        SOFTIRQ_TIME_IN_SECONDS_TOTAL,
        STEAL_TIME_IN_SECONDS_TOTAL
    }



    protected final Iterable<Tag> tags;

    protected final CalculatedCpuMetrics calculatedCpuMetrics;

    static long TICKS_PER_SEC = -1;

    static {
        if (Platform.isMac() || Platform.isLinux()) {
            TICKS_PER_SEC = ParseUtil.parseLongOrDefault(ExecutingCommand.getFirstAnswer("getconf CLK_TCK"),
                -1L);
            if (TICKS_PER_SEC == -1) {
                LOG.info("Unable to determine ticks per second, cumulative cpu time in seconds will be disabled");
            }
        } else {
            LOG.info("Cumulative cpu time in seconds is unsupported on this platform");
        }
    }

    public static boolean isCpuTotalSecondsSupported() {
        return TICKS_PER_SEC != -1;
    }

    public OshiProcessorMetrics() {
        this(Collections.emptyList(), CpuSampleType.ALL);
    }

    public OshiProcessorMetrics(Iterable<Tag> tags, CpuSampleType cpuSampleType) {
        this.tags = tags;
        this.calculatedCpuMetrics =
            new CalculatedCpuMetrics(new SystemInfo().getHardware().getProcessor(), cpuSampleType);
    }

    public OshiProcessorMetrics(CpuSampleType type) {
        this(Collections.emptyList(), type);
    }

    /**
     * Read the value of a specific value. This method mainly exists
     * to make sure that refresh() is invoked properly instead of invoking it in
     * each Meter.
     */
    private double getCpuMetricAsDouble(ProcessorMetric type) {
        calculatedCpuMetrics.refresh();

        switch (type) {
            case USER_PERCENT: return calculatedCpuMetrics.user;
            case NICE_PERCENT: return calculatedCpuMetrics.nice;
            case SYSTEM_PERCENT: return calculatedCpuMetrics.sys;
            case IDLE_PERCENT: return calculatedCpuMetrics.idle;
            case IOWAIT_PERCENT: return calculatedCpuMetrics.iowait;
            case IRQ_PERCENT: return calculatedCpuMetrics.irq;
            case SOFTIRQ_PERCENT: return calculatedCpuMetrics.softirq;
            case STEAL_PERCENT: return calculatedCpuMetrics.steal;

            case LOAD_1M: return calculatedCpuMetrics.load1m;
            case LOAD_5M: return calculatedCpuMetrics.load5m;
            case LOAD_15M: return calculatedCpuMetrics.load15m;

            case USER_TIME_SECONDS_TOTAL: return calculatedCpuMetrics.userTimeSecondsTotal;
            case NICE_TIME_SECONDS_TOTAL: return calculatedCpuMetrics.niceTimeInSecondsTotal;
            case SYS_TIME_SECONDS_TOTAL: return calculatedCpuMetrics.sysTimeInSecondsTotal;
            case IDLE_TIME_SECONDS_TOTAL: return calculatedCpuMetrics.idleTimeInSecondsTotal;
            case IOWAIT_TIME_IN_SECONDS_TOTAL: return calculatedCpuMetrics.iowaitTimeInSecondsTotal;
            case IRQ_TIME_IN_SECONDS_TOTAL: return calculatedCpuMetrics.irqTimeInSecondsTotal;
            case SOFTIRQ_TIME_IN_SECONDS_TOTAL: return calculatedCpuMetrics.softirqTimeInSecondsTotal;
            case STEAL_TIME_IN_SECONDS_TOTAL: return calculatedCpuMetrics.stealTimeInSecondsTotal;
            default: return 0;
        }
    }

    /**
     * Calculates and caches CPU load, CPU percent and cumulative cpu time in seconds.
     *
     */
    protected static class CalculatedCpuMetrics {

        public final CpuSampleType cpuSampleType;

        // Why not make it thread-safe?
        private final Lock lock = new ReentrantLock();

        private long refreshTime = 0;
        private final CentralProcessor processor;

        private long prevTicks[] = null;


        private volatile double user;
        private volatile double nice;
        private volatile double sys;
        private volatile double idle;
        private volatile double iowait;
        private volatile double irq;
        private volatile double softirq;
        private volatile double steal;

        private volatile double load1m;
        private volatile double load5m;
        private volatile double load15m;

        private volatile long userTimeSecondsTotal;
        private volatile long niceTimeInSecondsTotal;
        private volatile long sysTimeInSecondsTotal;
        private volatile long idleTimeInSecondsTotal;
        private volatile long iowaitTimeInSecondsTotal;
        private volatile long irqTimeInSecondsTotal;
        private volatile long softirqTimeInSecondsTotal;
        private volatile long stealTimeInSecondsTotal;

        public CalculatedCpuMetrics(CentralProcessor processor) {
            this.processor = processor;
            cpuSampleType = CpuSampleType.ALL;
        }

        public CalculatedCpuMetrics(CentralProcessor processor, CpuSampleType cpuSampleType) {
            this.processor = processor;
            this.cpuSampleType = cpuSampleType;
        }


        public  void refresh() {
            if (lock.tryLock()) {
                try {
                    doRefresh();
                } finally {
                    lock.unlock();
                }
            }
        }

        // Not sure if there are any concurrent threads involved?
        private  void doRefresh() {

            // First invocation
            if (prevTicks == null) {
                prevTicks = processor.getSystemCpuLoadTicks();
                return;
            }

            // quick and dirty to prevent multiple refreshes
            if (System.currentTimeMillis() - refreshTime < MIN_REFRESH_INTERVAL) {
                return;
            }

            double[] systemLoadAverage = processor.getSystemLoadAverage(3);
            load1m = systemLoadAverage[0] < 0 ? 0 : systemLoadAverage[0];
            load5m = systemLoadAverage[1] < 0 ? 0 : systemLoadAverage[1];
            load15m = systemLoadAverage[2] < 0 ? 0 : systemLoadAverage[2];

            long[] ticks = processor.getSystemCpuLoadTicks();

            if (cpuSampleType == CpuSampleType.ALL || cpuSampleType == CpuSampleType.PERCENT) {
                long user = ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()];
                long nice = ticks[CentralProcessor.TickType.NICE.getIndex()] - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
                long sys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
                long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
                long iowait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()] - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
                long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()] - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
                long softirq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
                long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()] - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
                long totalCpu = user + nice + sys + idle + iowait + irq + softirq + steal;

                this.user = 100d * user / totalCpu;
                this.nice = 100d * nice / totalCpu;
                this.sys = 100d * sys / totalCpu;
                this.idle = 100d * idle / totalCpu;
                this.iowait = 100d * iowait / totalCpu;
                this.irq = 100d * irq / totalCpu;
                this.softirq = 100d * softirq / totalCpu;
                this.steal = 100d * steal / totalCpu;
            }

            if (isCpuTotalSecondsSupported() && (cpuSampleType == CpuSampleType.ALL || cpuSampleType == CpuSampleType.SECONDS_TOTAL)) {
                this.userTimeSecondsTotal = ticks[CentralProcessor.TickType.USER.getIndex()] / TICKS_PER_SEC;
                this.niceTimeInSecondsTotal = ticks[CentralProcessor.TickType.NICE.getIndex()] / TICKS_PER_SEC;
                this.sysTimeInSecondsTotal = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] / TICKS_PER_SEC;
                this.idleTimeInSecondsTotal = ticks[CentralProcessor.TickType.IDLE.getIndex()] / TICKS_PER_SEC;
                this.iowaitTimeInSecondsTotal = ticks[CentralProcessor.TickType.IOWAIT.getIndex()] / TICKS_PER_SEC;
                this.irqTimeInSecondsTotal = ticks[CentralProcessor.TickType.IRQ.getIndex()] / TICKS_PER_SEC;
                this.softirqTimeInSecondsTotal = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] / TICKS_PER_SEC;
                this.stealTimeInSecondsTotal = ticks[CentralProcessor.TickType.STEAL.getIndex()] / TICKS_PER_SEC;
            }

            refreshTime = System.currentTimeMillis();
            prevTicks = ticks;
        }
    }


    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        if (calculatedCpuMetrics.cpuSampleType == CpuSampleType.ALL ||
            calculatedCpuMetrics.cpuSampleType == CpuSampleType.SECONDS_TOTAL) {
            cpuUsageInSeconds(meterRegistry);
        }
        if (calculatedCpuMetrics.cpuSampleType == CpuSampleType.ALL ||
            calculatedCpuMetrics.cpuSampleType == CpuSampleType.PERCENT) {
            cpuUsagePercent(meterRegistry);
        }
        systemLoad(meterRegistry);
    }


    private void cpuUsagePercent(MeterRegistry meterRegistry) {


        Gauge.builder("system.cpu.usage.pct", ProcessorMetric.USER_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "user")
            .description("User cpu usage in percent")
            .register(meterRegistry);

        Gauge.builder("system.cpu.usage.pct", ProcessorMetric.SYSTEM_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "system")
            .description("System cpu usage in percent")
            .register(meterRegistry);

        Gauge.builder("system.cpu.usage.pct",  ProcessorMetric.IDLE_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "idle")
            .description("Idle cpu usage in percent")
            .register(meterRegistry);

        Gauge.builder("system.cpu.usage.pct",  ProcessorMetric.NICE_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "nice")
            .description("Nice cpu usage in percent")
            .register(meterRegistry);

        Gauge.builder("system.cpu.usage.pct",  ProcessorMetric.IRQ_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "irq")
            .description("IRQ cpu usage in percent")
            .register(meterRegistry);

        Gauge.builder("system.cpu.usage.pct",  ProcessorMetric.SOFTIRQ_PERCENT, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("mode", "irq")
            .description("SOFTIRQ cpu usage in percent")
            .register(meterRegistry);
    }

    private void systemLoad(MeterRegistry meterRegistry) {

        int cpuCount = calculatedCpuMetrics.processor.getPhysicalProcessorCount();

        Gauge.builder("system.load.1m",  ProcessorMetric.LOAD_1M, this::getCpuMetricAsDouble )
            .tags(tags)
            .tag("cpus", String.valueOf(cpuCount))
            .description("System load 1m")
            .register(meterRegistry);

        Gauge.builder("system.load.5m",  ProcessorMetric.LOAD_5M, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("cpus", String.valueOf(cpuCount))
            .description("System load 5m")
            .register(meterRegistry);

        Gauge.builder("system.load.15m",  ProcessorMetric.LOAD_15M, this::getCpuMetricAsDouble)
            .tags(tags)
            .tag("cpus", String.valueOf(cpuCount))
            .description("System load 15m")
            .register(meterRegistry);
    }


    private void cpuUsageInSeconds(MeterRegistry meterRegistry) {

        if (isCpuTotalSecondsSupported()) {

            FunctionCounter.builder("system.cpu.seconds.total", USER_TIME_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "user")
                .description("User cpu usage in percent")
                .register(meterRegistry);

            FunctionCounter.builder("system.cpu.seconds.total", IDLE_TIME_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "idle")
                .description("User cpu idle in percent")
                .register(meterRegistry);

            FunctionCounter.builder("system.cpu.seconds.total", SYS_TIME_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "system")
                .description("User cpu usage in percent")
                .register(meterRegistry);

            FunctionCounter.builder("system.cpu.seconds.total", IRQ_TIME_IN_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "irq")
                .description("User cpu irq in percent")
                .register(meterRegistry);

            FunctionCounter.builder("system.cpu.seconds.total", SOFTIRQ_TIME_IN_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "softirq")
                .description("User cpu softirq in percent")
                .register(meterRegistry);

            FunctionCounter.builder("system.cpu.seconds.total", STEAL_TIME_IN_SECONDS_TOTAL, this::getCpuMetricAsDouble)
                .tags(tags)
                .tag("mode", "steal")
                .description("User cpu steal in percent")
                .register(meterRegistry);

        } else {
            LOG.info("Cunulative cpu seconds counter is unsupported on this platform");
        }
    }
}
