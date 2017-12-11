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
package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.catalina.Manager;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TomcatMetrics implements MeterBinder {
    private final Manager manager;
    private final Duration maxRegistrationWait;
    private final Supplier<MBeanServer> mBeanServerProvider;

    private Iterable<Tag> tags;

    public static void monitor(MeterRegistry meterRegistry, Manager manager, String... tags) {
        monitor(meterRegistry, manager, Tags.zip(tags));
    }

    public static void monitor(MeterRegistry meterRegistry, Manager manager, Iterable<Tag> tags) {
        new TomcatMetrics(manager, tags).bindTo(meterRegistry);
    }

    public TomcatMetrics(Manager manager, Iterable<Tag> tags) {
        this(manager, tags, Duration.ofHours(24), ManagementFactory::getPlatformMBeanServer);
    }

    public TomcatMetrics(Manager manager, Iterable<Tag> tags, Duration maxRegistrationWait, Supplier<MBeanServer> mBeanServerProvider) {
        this.tags = tags;
        this.manager = manager;
        this.maxRegistrationWait = maxRegistrationWait;
        this.mBeanServerProvider = mBeanServerProvider;
    }

    @Override
    public void bindTo(MeterRegistry reg) {
        Thread tomcatRegisterThread = new Thread(() -> {
            waitForTomcatJmxRegistration();

            MBeanServer server = mBeanServerProvider.get();
            registerGlobalRequestMetrics(reg, server);
            registerServletMetrics(reg, server);
            registerCacheMetrics(reg, server);
            registerThreadPoolMetrics(reg, server);
        });
        tomcatRegisterThread.setName("tomcat-jmx-metrics-register");
        tomcatRegisterThread.start();

        if (manager == null) {
            //If the binder is created but unable to find the session manager don't register those metrics
            return;
        }

        Gauge.builder("tomcat.sessions.active.max", manager, Manager::getMaxActive)
            .tags(tags)
            .register(reg);
        Gauge.builder("tomcat.sessions.active.current", manager, Manager::getActiveSessions)
            .tags(tags)
            .register(reg);
        FunctionCounter.builder("tomcat.sessions.expired", manager, Manager::getExpiredSessions)
            .tags(tags)
            .register(reg);
        FunctionCounter.builder("tomcat.sessions.rejected", manager, Manager::getRejectedSessions)
            .tags(tags)
            .register(reg);
        TimeGauge.builder("tomcat.sessions.alive.max", manager, TimeUnit.SECONDS, Manager::getSessionMaxAliveTime)
            .tags(tags)
            .register(reg);

        FunctionCounter.builder("tomcat.sessions.created", manager, Manager::getSessionCounter)
            .tags(tags)
            .register(reg);
    }


    private void waitForTomcatJmxRegistration() {
        MBeanServer server = mBeanServerProvider.get();
        long endWait = System.currentTimeMillis() + maxRegistrationWait.toMillis();

        //noinspection InfiniteLoopStatement
        while (System.currentTimeMillis() < endWait) {
            try {
                if(!server.queryNames(new ObjectName("Tomcat:type=GlobalRequestProcessor,*"), null).isEmpty()){
                    return;
                }
                Thread.sleep(500);
            } catch (Exception e) {
                //NO OP
            }
        }

    }

    private void registerThreadPoolMetrics(MeterRegistry reg, MBeanServer server) {
        for (ObjectName name : findNames(server, "Tomcat:type=ThreadPool,*")) {
            Iterable<Tag> tags = Tags.concat(this.tags, safeTags(name, "name"));

            Gauge.builder("tomcat.threads.config.max", server,
                s -> safeDouble(() -> s.getAttribute(name, "maxThreads")))
                .tags(tags)
                .register(reg);

            Gauge.builder("tomcat.threads.busy", server,
                s -> safeDouble(() -> s.getAttribute(name, "currentThreadsBusy")))
                .tags(tags)
                .register(reg);
            Gauge.builder("tomcat.threads.current", server,
                s -> safeDouble(() -> s.getAttribute(name, "currentThreadCount")))
                .tags(tags)
                .register(reg);
        }
    }


    private void registerCacheMetrics(MeterRegistry reg, MBeanServer server) {
        for (ObjectName name : findNames(server, "Tomcat:type=StringCache,*")) {
            FunctionCounter.builder("tomcat.cache.access", server,
                s -> safeDouble(() -> s.getAttribute(name, "accessCount")))
                .register(reg);

            FunctionCounter.builder("tomcat.cache.hit", server,
                s -> safeDouble(() -> s.getAttribute(name, "hitCount")))
                .register(reg);
        }
    }

    private void registerServletMetrics(MeterRegistry reg, MBeanServer server) {
        for (ObjectName name : findNames(server, "Tomcat:j2eeType=Servlet,*")) {
            Iterable<Tag> tags = Tags.concat(this.tags, safeTags(name, "name"));

            FunctionCounter.builder("tomcat.servlet.error", server,
                s -> safeDouble(() -> s.getAttribute(name, "errorCount")))
                .tags(tags)
                .register(reg);

            FunctionTimer.builder("tomcat.servlet.request", server,
                s -> safeLong(() -> s.getAttribute(name, "requestCount")),
                s -> safeDouble(() -> s.getAttribute(name, "processingTime")), TimeUnit.MILLISECONDS)
                .tags(tags)
                .register(reg);

            TimeGauge.builder("tomcat.servlet.request.max", server, TimeUnit.MILLISECONDS,
                s -> safeDouble(() -> s.getAttribute(name, "maxTime")))
                .tags(tags)
                .register(reg);
        }
    }

    private void registerGlobalRequestMetrics(MeterRegistry reg, MBeanServer server) {
        for (ObjectName name : findNames(server, "Tomcat:type=GlobalRequestProcessor,*")) {
            Iterable<Tag> tags = Tags.concat(this.tags, safeTags(name, "name"));

            FunctionCounter.builder("tomcat.global.sent", server,
                s -> safeDouble(() -> s.getAttribute(name, "bytesSent")))
                .tags(tags)
                .baseUnit("bytes")
                .register(reg);
            FunctionCounter.builder("tomcat.global.received", server,
                s -> safeDouble(() -> s.getAttribute(name, "bytesReceived")))
                .tags(tags)
                .baseUnit("bytes")
                .register(reg);

            FunctionCounter.builder("tomcat.global.error", server,
                s -> safeDouble(() -> s.getAttribute(name, "errorCount")))
                .tags(tags)
                .register(reg);

            FunctionTimer.builder("tomcat.global.request", server,
                s -> safeLong(() -> s.getAttribute(name, "requestCount")),
                s -> safeDouble(() -> s.getAttribute(name, "processingTime")), TimeUnit.MILLISECONDS)
                .tags(tags)
                .register(reg);

            TimeGauge.builder("tomcat.global.request.max", server, TimeUnit.MILLISECONDS,
                s -> safeDouble(() -> s.getAttribute(name, "maxTime")))
                .tags(tags)
                .register(reg);
        }
    }

    private Set<ObjectName> findNames(MBeanServer server, String query) {
        try {
            return server.queryNames(new ObjectName(query), null);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Error registering Tomcat JMX based metrics", e);
        }
    }


    private double safeDouble(Callable<Object> callable) {
        try {
            return Double.parseDouble(callable.call().toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long safeLong(Callable<Object> callable) {
        try {
            return Long.parseLong(callable.call().toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Iterable<Tag> safeTags(ObjectName name, String tagName) {
        if (name.getKeyProperty(tagName) != null) {
            return Tags.zip(tagName, name.getKeyProperty(tagName).replaceAll("\"", ""));
        } else {
            return Collections.emptyList();
        }
    }
}
