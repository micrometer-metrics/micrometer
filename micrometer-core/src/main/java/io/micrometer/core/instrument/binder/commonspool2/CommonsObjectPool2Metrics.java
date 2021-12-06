/**
 * Copyright 2020 Pivotal Software, Inc.
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

package io.micrometer.core.instrument.binder.commonspool2;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.emptyList;

/**
 * Apache Commons Pool 2.x metrics collected from metrics exposed via the MBeanServer.
 * Metrics are exposed for each object pool.
 *
 * @author Chao Chang
 * @since 1.6.0
 */
public class CommonsObjectPool2Metrics implements MeterBinder, AutoCloseable {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(CommonsObjectPool2Metrics.class);
    private static final String JMX_DOMAIN = "org.apache.commons.pool2";
    private static final String METRIC_NAME_PREFIX = "commons.pool2.";

    private static final String[] TYPES = new String[]{"GenericObjectPool", "GenericKeyedObjectPool"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MBeanServer mBeanServer;
    private final Iterable<Tag> tags;
    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    public CommonsObjectPool2Metrics() {
        this(emptyList());
    }

    public CommonsObjectPool2Metrics(Iterable<Tag> tags) {
        this(getMBeanServer(), tags);
    }

    public CommonsObjectPool2Metrics(MBeanServer mBeanServer, Iterable<Tag> tags) {
        this.mBeanServer = mBeanServer;
        this.tags = tags;
    }

    private static MBeanServer getMBeanServer() {
        List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
        if (!mBeanServers.isEmpty()) {
            return mBeanServers.get(0);
        }
        return ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        for (String type : TYPES) {
            registerMetricsEventually(
                    type,
                    (o, tags) -> {
                        registerGaugeForObject(registry, o,
                                "NumIdle", "num.idle", tags,
                                "The number of instances currently idle in this pool", BaseUnits.OBJECTS);
                        registerGaugeForObject(registry, o,
                                "NumActive", "num.active", tags,
                                "The number of instances currently active in this pool", BaseUnits.OBJECTS);
                        registerGaugeForObject(registry, o,
                                "NumWaiters", "num.waiters", tags,
                                "The estimate of the number of threads currently blocked waiting for an object from the pool",
                                BaseUnits.THREADS);

                        registerFunctionCounterForObject(registry, o,
                                "CreatedCount", "created", tags,
                                "The total number of objects created for this pool over the lifetime of the pool",
                                BaseUnits.OBJECTS);
                        registerFunctionCounterForObject(registry, o,
                                "BorrowedCount", "borrowed", tags,
                                "The total number of objects successfully borrowed from this pool over the lifetime of the pool",
                                BaseUnits.OBJECTS);
                        registerFunctionCounterForObject(registry, o,
                                "ReturnedCount", "returned", tags,
                                "The total number of objects returned to this pool over the lifetime of the pool",
                                BaseUnits.OBJECTS);
                        registerFunctionCounterForObject(registry, o,
                                "DestroyedCount", "destroyed", tags,
                                "The total number of objects destroyed by this pool over the lifetime of the pool",
                                BaseUnits.OBJECTS);
                        registerFunctionCounterForObject(registry, o,
                                "DestroyedByEvictorCount", "destroyed.by.evictor", tags,
                                "The total number of objects destroyed by the evictor associated with this pool over the lifetime of the pool",
                                BaseUnits.OBJECTS);
                        registerFunctionCounterForObject(registry, o,
                                "DestroyedByBorrowValidationCount", "destroyed.by.borrow.validation", tags,
                                "The total number of objects destroyed by this pool as a result of failing validation during borrowObject() over the lifetime of the pool",
                                BaseUnits.OBJECTS);

                        registerTimeGaugeForObject(registry, o,
                                "MaxBorrowWaitTimeMillis", "max.borrow.wait", tags,
                                "The maximum time a thread has waited to borrow objects from the pool");
                        registerTimeGaugeForObject(registry, o,
                                "MeanActiveTimeMillis", "mean.active", tags,
                                "The mean time objects are active");
                        registerTimeGaugeForObject(registry, o,
                                "MeanIdleTimeMillis", "mean.idle", tags,
                                "The mean time objects are idle");
                        registerTimeGaugeForObject(registry, o,
                                "MeanBorrowWaitTimeMillis", "mean.borrow.wait", tags,
                                "The mean time threads wait to borrow an object");
                    });
        }
    }

    private Iterable<Tag> nameTag(ObjectName name, String type)
            throws AttributeNotFoundException, MBeanException, ReflectionException,
            InstanceNotFoundException {
        Tags tags = Tags.of("name", name.getKeyProperty("name"), "type", type);
        if (Objects.equals(type, "GenericObjectPool")) {
            // for GenericObjectPool, we want to include the name and factoryType as tags
            String factoryType = mBeanServer.getAttribute(name, "FactoryType").toString();
            tags = Tags.concat(tags, "factoryType", factoryType);
        }
        return tags;
    }

    private void registerMetricsEventually(String type, BiConsumer<ObjectName, Tags> perObject) {
        try {
            Set<ObjectName> objs =
                    mBeanServer.queryNames(new ObjectName(JMX_DOMAIN + ":type=" + type + ",*"), null);
            for (ObjectName o : objs) {
                Iterable<Tag> nameTags = emptyList();
                try {
                    nameTags = nameTag(o, type);
                } catch (Exception e) {
                    log.error("exception in determining name tag", e);
                }
                perObject.accept(o, Tags.concat(tags, nameTags));
            }
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Error registering commons pool2 based metrics", e);
        }

        registerNotificationListener(type, perObject);
    }

    /**
     * This notification listener should remain indefinitely since new pools can be added at
     * any time.
     *
     * @param type      The pool type to listen for.
     * @param perObject Metric registration handler when a new MBean is created.
     */
    private void registerNotificationListener(String type, BiConsumer<ObjectName, Tags> perObject) {
        NotificationListener notificationListener =
                // in notification listener, we cannot get attributes for the registered object,
                // so we do it later time in a separate thread.
                (notification, handback) -> {
                    executor.execute(
                            () -> {
                                MBeanServerNotification mbs = (MBeanServerNotification) notification;
                                ObjectName o = mbs.getMBeanName();
                                Iterable<Tag> nameTags = emptyList();
                                int maxTries = 3;
                                for (int i = 0; i < maxTries; i++) {
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException(e);
                                    }
                                    try {
                                        nameTags = nameTag(o, type);
                                        break;
                                    } catch (AttributeNotFoundException
                                            | MBeanException
                                            | ReflectionException
                                            | InstanceNotFoundException e) {
                                        if (i == maxTries - 1) {
                                            log.error("can not set name tag", e);
                                        }
                                    }
                                }
                                perObject.accept(o, Tags.concat(tags, nameTags));
                            });
                };

        NotificationFilter filter =
                (NotificationFilter)
                        notification -> {
                            if (!MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType()))
                                return false;
                            ObjectName obj = ((MBeanServerNotification) notification).getMBeanName();
                            return obj.getDomain().equals(JMX_DOMAIN) && obj.getKeyProperty("type").equals(type);
                        };

        try {
            mBeanServer.addNotificationListener(
                    MBeanServerDelegate.DELEGATE_NAME, notificationListener, filter, null);
            notificationListenerCleanUpRunnables.add(
                    () -> {
                        try {
                            mBeanServer.removeNotificationListener(
                                    MBeanServerDelegate.DELEGATE_NAME, notificationListener);
                        } catch (InstanceNotFoundException | ListenerNotFoundException ignore) {
                        }
                    });
        } catch (InstanceNotFoundException ignore) {
            // unable to register MBean listener
        }
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
        executor.shutdown();
    }

    private void registerGaugeForObject(
            MeterRegistry registry,
            ObjectName o,
            String jmxMetricName,
            String meterName,
            Tags allTags,
            String description,
            @Nullable String baseUnit) {
        final AtomicReference<Gauge> gauge = new AtomicReference<>();
        gauge.set(Gauge
                .builder(
                        METRIC_NAME_PREFIX + meterName,
                        mBeanServer,
                        getJmxAttribute(registry, gauge, o, jmxMetricName)
                )
                .description(description)
                .baseUnit(baseUnit)
                .tags(allTags)
                .register(registry)
        );
    }

    private void registerFunctionCounterForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, String meterName, Tags allTags, String description, @Nullable String baseUnit) {
        final AtomicReference<FunctionCounter> counter = new AtomicReference<>();
        counter.set(FunctionCounter
                .builder(
                        METRIC_NAME_PREFIX + meterName,
                        mBeanServer,
                        getJmxAttribute(registry, counter, o, jmxMetricName)
                )
                .description(description)
                .baseUnit(baseUnit)
                .tags(allTags)
                .register(registry)
        );
    }

    private void registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
                                            String meterName, Tags allTags, String description) {
        final AtomicReference<TimeGauge> timeGauge = new AtomicReference<>();
        timeGauge.set(TimeGauge
                .builder(
                        METRIC_NAME_PREFIX + meterName,
                        mBeanServer,
                        TimeUnit.MILLISECONDS,
                        getJmxAttribute(registry, timeGauge, o, jmxMetricName)
                )
                .description(description)
                .tags(allTags)
                .register(registry)
        );
    }

    private ToDoubleFunction<MBeanServer> getJmxAttribute(
            MeterRegistry registry,
            AtomicReference<? extends Meter> meter,
            ObjectName o,
            String jmxMetricName) {
        return s -> safeDouble(
                () -> {
                    if (!s.isRegistered(o)) {
                        registry.remove(meter.get());
                    }
                    return s.getAttribute(o, jmxMetricName);
                });
    }

    private double safeDouble(Callable<Object> callable) {
        try {
            return Double.parseDouble(callable.call().toString());
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
