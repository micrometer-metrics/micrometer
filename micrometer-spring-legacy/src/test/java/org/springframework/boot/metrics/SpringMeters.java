package org.springframework.boot.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.metrics.binder.DataSourceMetrics;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

import java.util.Collection;

import static java.util.Arrays.asList;

public class SpringMeters {
    /**
     * Record metrics on active connections and connection pool utilization.
     *
     * @param registry          The registry to bind metrics to.
     * @param dataSource        The data source to instrument.
     * @param metadataProviders A list of providers from which the instrumentation can look up information about pool usage.
     * @param name              The name prefix of the metrics.
     * @param tags              Tags to apply to all recorded metrics.
     * @return The instrumented data source, unchanged. The original data source
     * is not wrapped or proxied in any way.
     */
    public static DataSource monitor(MeterRegistry registry,
                                     DataSource dataSource,
                                     Collection<DataSourcePoolMetadataProvider> metadataProviders,
                                     String name,
                                     Iterable<Tag> tags) {
        new DataSourceMetrics(dataSource, metadataProviders, name, tags).bindTo(registry);
        return dataSource;
    }

    /**
     * Record metrics on active connections and connection pool utilization.
     *
     * @param registry          The registry to bind metrics to.
     * @param dataSource        The data source to instrument.
     * @param metadataProviders A list of providers from which the instrumentation can look up information about pool usage.
     * @param name              The name prefix of the metrics
     * @param tags              Tags to apply to all recorded metrics.
     * @return The instrumented data source, unchanged. The original data source
     * is not wrapped or proxied in any way.
     */
    public static DataSource monitor(MeterRegistry registry,
                                     DataSource dataSource,
                                     Collection<DataSourcePoolMetadataProvider> metadataProviders,
                                     String name,
                                     Tag... tags) {
        return monitor(registry, dataSource, metadataProviders, name, asList(tags));
    }

    /**
     * Record metrics on the use of a {@link ThreadPoolTaskExecutor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The task executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ThreadPoolTaskExecutor monitor(MeterRegistry registry, ThreadPoolTaskExecutor executor, String name, Iterable<Tag> tags) {
        Meters.monitor(registry, executor.getThreadPoolExecutor(), name, tags);
        return executor;
    }

    /**
     * Record metrics on the use of a {@link ThreadPoolTaskExecutor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ThreadPoolTaskExecutor monitor(MeterRegistry registry, ThreadPoolTaskExecutor executor, String name, Tag... tags) {
        Meters.monitor(registry, executor.getThreadPoolExecutor(), name, tags);
        return executor;
    }

    /**
     * Record metrics on the use of a {@link ThreadPoolTaskExecutor}.
     *
     * @param registry  The registry to bind metrics to.
     * @param scheduler The task scheduler to instrument.
     * @param name      The name prefix of the metrics.
     * @param tags      Tags to apply to all recorded metrics.
     * @return The instrumented scheduler, proxied.
     */
    public static ThreadPoolTaskScheduler monitor(MeterRegistry registry, ThreadPoolTaskScheduler scheduler, String name, Iterable<Tag> tags) {
        Meters.monitor(registry, scheduler.getScheduledExecutor(), name, tags);
        return scheduler;
    }

    /**
     * Record metrics on the use of a {@link ThreadPoolTaskExecutor}.
     *
     * @param registry  The registry to bind metrics to.
     * @param scheduler The scheduler to instrument.
     * @param name      The name prefix of the metrics.
     * @param tags      Tags to apply to all recorded metrics.
     * @return The instrumented scheduler, proxied.
     */
    public static ThreadPoolTaskScheduler monitor(MeterRegistry registry, ThreadPoolTaskScheduler scheduler, String name, Tag... tags) {
        Meters.monitor(registry, scheduler.getScheduledExecutor(), name, tags);
        return scheduler;
    }
}
