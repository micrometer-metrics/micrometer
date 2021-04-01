package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class DynatraceMeterRegistryImplBase {
    protected static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace-metrics-publisher");

    protected DynatraceConfig config;
    protected Clock clock;
    protected ThreadFactory threadFactory;
    protected HttpSender httpClient;

    public abstract void publish(MeterRegistry registry);

    public TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public DynatraceMeterRegistryImplBase(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        this.config = config;
        this.clock = clock;
        this.threadFactory = threadFactory;
        this.httpClient = httpClient;
    }
}
