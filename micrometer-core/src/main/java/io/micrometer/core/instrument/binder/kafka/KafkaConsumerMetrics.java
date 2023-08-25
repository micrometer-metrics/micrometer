/*
 * Copyright 2018 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.emptyList;

/**
 * Kafka consumer metrics collected from metrics exposed by Kafka consumers via the
 * MBeanServer. Metrics are exposed at each consumer thread.
 * <p>
 * Metric names here are based on the naming scheme as it was last changed in Kafka
 * version 0.11.0. Metrics for earlier versions of Kafka will not report correctly.
 *
 * @author Wardha Perinkadakattu
 * @author Jon Schneider
 * @author Johnny Lim
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring
 * documentation</a>
 * @since 1.1.0
 * @deprecated use {@link KafkaClientMetrics} instead since 1.4.0
 */
@Incubating(since = "1.1.0")
@NonNullApi
@NonNullFields
@Deprecated
public class KafkaConsumerMetrics implements MeterBinder, AutoCloseable {

    private static final String JMX_DOMAIN = "kafka.consumer";

    private static final String METRIC_NAME_PREFIX = "kafka.consumer.";

    private final MBeanServer mBeanServer;

    private final Iterable<Tag> tags;

    @Nullable
    private Integer kafkaMajorVersion;

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    public KafkaConsumerMetrics() {
        this(emptyList());
    }

    public KafkaConsumerMetrics(Iterable<Tag> tags) {
        this(getMBeanServer(), tags);
    }

    public KafkaConsumerMetrics(MBeanServer mBeanServer, Iterable<Tag> tags) {
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
    public void bindTo(MeterRegistry registry) {
        registerMetricsEventually(registry, "consumer-fetch-manager-metrics", (o, tags) -> {
            List<Meter> meters = new ArrayList<>();

            // metrics reported per consumer, topic and partition
            if (tags.stream().anyMatch(t -> t.getKey().equals("topic"))
                    && tags.stream().anyMatch(t -> t.getKey().equals("partition"))) {
                meters.add(registerGaugeForObject(registry, o, "records-lag", tags, "The latest lag of the partition",
                        "records"));
                meters.add(registerGaugeForObject(registry, o, "records-lag-avg", tags,
                        "The average lag of the partition", "records"));
                meters.add(registerGaugeForObject(registry, o, "records-lag-max", tags,
                        "The maximum lag in terms of number of records for any partition in this window. An increasing value over time is your best indication that the consumer group is not keeping up with the producers.",
                        "records"));
                if (kafkaMajorVersion(tags) >= 2) {
                    // KAFKA-6184
                    meters.add(registerGaugeForObject(registry, o, "records-lead", tags,
                            "The latest lead of the partition.", "records"));
                    meters.add(registerGaugeForObject(registry, o, "records-lead-min", tags,
                            "The min lead of the partition. The lag between the consumer offset and the start offset of the log. If this gets close to zero, it's an indication that the consumer may lose data soon.",
                            "records"));
                    meters.add(registerGaugeForObject(registry, o, "records-lead-avg", tags,
                            "The average lead of the partition.", "records"));
                }
                // metrics reported per consumer and topic
            }
            else if (tags.stream().anyMatch(t -> t.getKey().equals("topic"))) {
                meters.add(registerGaugeForObject(registry, o, "fetch-size-avg", tags,
                        "The average number of bytes fetched per request.", BaseUnits.BYTES));
                meters.add(registerGaugeForObject(registry, o, "fetch-size-max", tags,
                        "The maximum number of bytes fetched per request.", BaseUnits.BYTES));
                meters.add(registerGaugeForObject(registry, o, "records-per-request-avg", tags,
                        "The average number of records in each request.", "records"));
                meters.add(registerFunctionCounterForObject(registry, o, "bytes-consumed-total", tags,
                        "The total number of bytes consumed.", BaseUnits.BYTES));
                meters.add(registerFunctionCounterForObject(registry, o, "records-consumed-total", tags,
                        "The total number of records consumed.", "records"));
                // metrics reported just per consumer
            }
            else {
                meters.add(registerFunctionCounterForObject(registry, o, "fetch-total", tags,
                        "The number of fetch requests.", "requests"));
                meters.add(registerTimeGaugeForObject(registry, o, "fetch-latency-avg", tags,
                        "The average time taken for a fetch request."));
                meters.add(registerTimeGaugeForObject(registry, o, "fetch-latency-max", tags,
                        "The max time taken for a fetch request."));
                meters.add(registerTimeGaugeForObject(registry, o, "fetch-throttle-time-avg", tags,
                        "The average throttle time. When quotas are enabled, the broker may delay fetch requests in order to throttle a consumer which has exceeded its limit. This metric indicates how throttling time has been added to fetch requests on average."));
                meters.add(registerTimeGaugeForObject(registry, o, "fetch-throttle-time-max", tags,
                        "The maximum throttle time."));
            }
            return meters;
        });

        registerMetricsEventually(registry, "consumer-coordinator-metrics", (o, tags) -> {
            List<Meter> meters = new ArrayList<>();

            meters.add(registerGaugeForObject(registry, o, "assigned-partitions", tags,
                    "The number of partitions currently assigned to this consumer.", "partitions"));
            meters.add(registerGaugeForObject(registry, o, "commit-rate", tags,
                    "The number of commit calls per second.", "commits"));
            meters.add(registerGaugeForObject(registry, o, "join-rate", tags,
                    "The number of group joins per second. Group joining is the first phase of the rebalance protocol. A large value indicates that the consumer group is unstable and will likely be coupled with increased lag.",
                    "joins"));
            meters.add(registerGaugeForObject(registry, o, "sync-rate", tags,
                    "The number of group syncs per second. Group synchronization is the second and last phase of the rebalance protocol. A large value indicates group instability.",
                    "syncs"));
            meters.add(registerGaugeForObject(registry, o, "heartbeat-rate", tags,
                    "The average number of heartbeats per second. After a rebalance, the consumer sends heartbeats to the coordinator to keep itself active in the group. You may see a lower rate than configured if the processing loop is taking more time to handle message batches. Usually this is OK as long as you see no increase in the join rate.",
                    "heartbeats"));

            meters.add(registerTimeGaugeForObject(registry, o, "commit-latency-avg", tags,
                    "The average time taken for a commit request."));
            meters.add(registerTimeGaugeForObject(registry, o, "commit-latency-max", tags,
                    "The max time taken for a commit request."));
            meters.add(registerTimeGaugeForObject(registry, o, "join-time-avg", tags,
                    "The average time taken for a group rejoin. This value can get as high as the configured session timeout for the consumer, but should usually be lower."));
            meters.add(registerTimeGaugeForObject(registry, o, "join-time-max", tags,
                    "The max time taken for a group rejoin. This value should not get much higher than the configured session timeout for the consumer."));
            meters.add(registerTimeGaugeForObject(registry, o, "sync-time-avg", tags,
                    "The average time taken for a group sync."));
            meters.add(registerTimeGaugeForObject(registry, o, "sync-time-max", tags,
                    "The max time taken for a group sync."));
            meters.add(registerTimeGaugeForObject(registry, o, "heartbeat-response-time-max", tags,
                    "The max time taken to receive a response to a heartbeat request."));
            meters.add(registerTimeGaugeForObject(registry, o, "last-heartbeat-seconds-ago", "last-heartbeat", tags,
                    "The time since the last controller heartbeat.", TimeUnit.SECONDS));
            return meters;
        });

        registerMetricsEventually(registry, "consumer-metrics", (o, tags) -> {
            List<Meter> meters = new ArrayList<>();

            meters.add(registerGaugeForObject(registry, o, "connection-count", tags,
                    "The current number of active connections.", "connections"));
            meters.add(registerGaugeForObject(registry, o, "connection-creation-total", tags,
                    "New connections established.", "connections"));
            meters.add(registerGaugeForObject(registry, o, "connection-close-total", tags, "Connections closed.",
                    "connections"));
            meters.add(registerGaugeForObject(registry, o, "io-ratio", tags,
                    "The fraction of time the I/O thread spent doing I/O.", null));
            meters.add(registerGaugeForObject(registry, o, "io-wait-ratio", tags,
                    "The fraction of time the I/O thread spent waiting.", null));
            meters.add(registerGaugeForObject(registry, o, "select-total", tags,
                    "Number of times the I/O layer checked for new I/O to perform.", null));

            meters.add(registerTimeGaugeForObject(registry, o, "io-time-ns-avg", "io-time-avg", tags,
                    "The average length of time for I/O per select call.", TimeUnit.NANOSECONDS));
            meters.add(registerTimeGaugeForObject(registry, o, "io-wait-time-ns-avg", "io-wait-time-avg", tags,
                    "The average length of time the I/O thread spent waiting for a socket to be ready for reads or writes.",
                    TimeUnit.NANOSECONDS));

            if (kafkaMajorVersion(tags) >= 2) {
                meters.add(registerGaugeForObject(registry, o, "successful-authentication-total",
                        "authentication-attempts", Tags.concat(tags, "result", "successful"),
                        "The number of successful authentication attempts.", null));
                meters.add(registerGaugeForObject(registry, o, "failed-authentication-total", "authentication-attempts",
                        Tags.concat(tags, "result", "failed"), "The number of failed authentication attempts.", null));

                meters.add(registerGaugeForObject(registry, o, "network-io-total", tags, "", BaseUnits.BYTES));
                meters.add(registerGaugeForObject(registry, o, "outgoing-byte-total", tags, "", BaseUnits.BYTES));
                meters.add(registerGaugeForObject(registry, o, "request-total", tags, "", "requests"));
                meters.add(registerGaugeForObject(registry, o, "response-total", tags, "", "responses"));

                meters.add(registerTimeGaugeForObject(registry, o, "io-waittime-total", "io-wait-time-total", tags,
                        "Time spent on the I/O thread waiting for a socket to be ready for reads or writes.",
                        TimeUnit.NANOSECONDS));
                meters.add(registerTimeGaugeForObject(registry, o, "iotime-total", "io-time-total", tags,
                        "Time spent in I/O during select calls.", TimeUnit.NANOSECONDS));
            }
            return meters;
        });
    }

    private Gauge registerGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, String meterName,
            Tags allTags, String description, @Nullable String baseUnit) {
        final AtomicReference<Gauge> gaugeReference = new AtomicReference<>();
        Gauge gauge = Gauge
            .builder(METRIC_NAME_PREFIX + meterName, mBeanServer,
                    getJmxAttribute(registry, gaugeReference, o, jmxMetricName))
            .description(description)
            .baseUnit(baseUnit)
            .tags(allTags)
            .register(registry);
        gaugeReference.set(gauge);
        return gauge;
    }

    private Gauge registerGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName, Tags allTags,
            String description, @Nullable String baseUnit) {
        return registerGaugeForObject(registry, o, jmxMetricName, sanitize(jmxMetricName), allTags, description,
                baseUnit);
    }

    private FunctionCounter registerFunctionCounterForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            Tags allTags, String description, @Nullable String baseUnit) {
        final AtomicReference<FunctionCounter> counterReference = new AtomicReference<>();
        FunctionCounter counter = FunctionCounter
            .builder(METRIC_NAME_PREFIX + sanitize(jmxMetricName), mBeanServer,
                    getJmxAttribute(registry, counterReference, o, jmxMetricName))
            .description(description)
            .baseUnit(baseUnit)
            .tags(allTags)
            .register(registry);
        counterReference.set(counter);
        return counter;
    }

    private TimeGauge registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            String meterName, Tags allTags, String description, TimeUnit timeUnit) {
        final AtomicReference<TimeGauge> timeGaugeReference = new AtomicReference<>();
        TimeGauge timeGauge = TimeGauge
            .builder(METRIC_NAME_PREFIX + meterName, mBeanServer, timeUnit,
                    getJmxAttribute(registry, timeGaugeReference, o, jmxMetricName))
            .description(description)
            .tags(allTags)
            .register(registry);
        timeGaugeReference.set(timeGauge);
        return timeGauge;
    }

    private TimeGauge registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            String meterName, Tags allTags, String description) {
        return registerTimeGaugeForObject(registry, o, jmxMetricName, meterName, allTags, description,
                TimeUnit.MILLISECONDS);
    }

    private ToDoubleFunction<MBeanServer> getJmxAttribute(MeterRegistry registry,
            AtomicReference<? extends Meter> meter, ObjectName o, String jmxMetricName) {
        return s -> safeDouble(() -> {
            if (!s.isRegistered(o)) {
                registry.remove(meter.get());
            }
            return s.getAttribute(o, jmxMetricName);
        });
    }

    private TimeGauge registerTimeGaugeForObject(MeterRegistry registry, ObjectName o, String jmxMetricName,
            Tags allTags, String description) {
        return registerTimeGaugeForObject(registry, o, jmxMetricName, sanitize(jmxMetricName), allTags, description);
    }

    int kafkaMajorVersion(Tags tags) {
        if (kafkaMajorVersion == null || kafkaMajorVersion == -1) {
            kafkaMajorVersion = tags.stream().filter(t -> "client.id".equals(t.getKey())).findAny().map(clientId -> {
                try {
                    String version = (String) mBeanServer.getAttribute(
                            new ObjectName(JMX_DOMAIN + ":type=app-info,client-id=" + clientId.getValue()), "version");
                    return Integer.parseInt(version.substring(0, version.indexOf('.')));
                }
                catch (Throwable e) {
                    // this can happen during application bootstrapping
                    // in this case, JMX bean is not available yet and version cannot be
                    // determined yet
                    return -1;
                }
            }).orElse(-1);
        }
        return kafkaMajorVersion;
    }

    private void registerMetricsEventually(MeterRegistry registry, String type,
            BiFunction<ObjectName, Tags, List<Meter>> perObject) {
        try {
            Set<ObjectName> objs = mBeanServer.queryNames(new ObjectName(JMX_DOMAIN + ":type=" + type + ",*"), null);
            if (!objs.isEmpty()) {
                for (ObjectName o : objs) {
                    List<Meter> meters = perObject.apply(o, Tags.concat(tags, nameTag(o)));
                    addUnregistrationListener(registry, type, o, meters);
                }
                return;
            }
        }
        catch (MalformedObjectNameException e) {
            throw new RuntimeException("Error registering Kafka JMX based metrics", e);
        }

        registerNotificationListener(registry, type, perObject);
    }

    /**
     * This notification listener should remain indefinitely since new Kafka consumers can
     * be added at any time.
     * @param type The Kafka JMX type to listen for.
     * @param perObject Metric registration handler when a new MBean is created.
     */
    private void registerNotificationListener(MeterRegistry registry, String type,
            BiFunction<ObjectName, Tags, List<Meter>> perObject) {
        NotificationListener registrationListener = (notification, handback) -> {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            ObjectName o = mbs.getMBeanName();
            List<Meter> meters = perObject.apply(o, Tags.concat(tags, nameTag(o)));
            addUnregistrationListener(registry, type, o, meters);
        };
        NotificationFilter registrationFilter = createNotificationFilter(type,
                MBeanServerNotification.REGISTRATION_NOTIFICATION);
        addNotificationListener(registrationListener, registrationFilter);
        notificationListenerCleanUpRunnables.add(() -> removeNotificationListener(registrationListener));
    }

    private void removeNotificationListener(NotificationListener notificationListener) {
        try {
            mBeanServer.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener);
        }
        catch (InstanceNotFoundException | ListenerNotFoundException ignored) {
        }
    }

    private void addUnregistrationListener(MeterRegistry registry, String type, ObjectName o, List<Meter> meters) {
        NotificationListener unregistrationListener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification2, Object handback2) {
                MBeanServerNotification mbs2 = (MBeanServerNotification) notification2;
                ObjectName o2 = mbs2.getMBeanName();
                if (o2.equals(o)) {
                    meters.stream().forEach(registry::remove);
                }
                removeNotificationListener(this);
            }
        };
        NotificationFilter unregistrationFilter = createNotificationFilter(type,
                MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
        addNotificationListener(unregistrationListener, unregistrationFilter);
    }

    private NotificationFilter createNotificationFilter(String type, String notificationType) {
        return (NotificationFilter) notification -> {
            if (!notificationType.equals(notification.getType())) {
                return false;
            }
            ObjectName obj = ((MBeanServerNotification) notification).getMBeanName();
            return obj.getDomain().equals(JMX_DOMAIN) && obj.getKeyProperty("type").equals(type);
        };
    }

    private void addNotificationListener(NotificationListener listener, NotificationFilter filter) {
        try {
            mBeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
        }
        catch (InstanceNotFoundException e) {
            throw new RuntimeException("Error registering Kafka MBean listener", e);
        }
    }

    private double safeDouble(Callable<Object> callable) {
        try {
            return Double.parseDouble(callable.call().toString());
        }
        catch (Exception e) {
            return Double.NaN;
        }
    }

    private Iterable<Tag> nameTag(ObjectName name) {
        Tags tags = Tags.empty();

        String clientId = name.getKeyProperty("client-id");
        if (clientId != null) {
            tags = Tags.concat(tags, "client.id", clientId);
        }

        String topic = name.getKeyProperty("topic");
        if (topic != null) {
            tags = Tags.concat(tags, "topic", topic);
        }

        String partition = name.getKeyProperty("partition");
        if (partition != null) {
            tags = Tags.concat(tags, "partition", partition);
        }

        return tags;
    }

    private static String sanitize(String value) {
        return value.replaceAll("-", ".");
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }

}
