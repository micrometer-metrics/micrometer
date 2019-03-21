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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.util.Collections;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Collects bytes and packets sent on all network interfaces an a machine.
 *
 * @author Johan Rask (jrask)
 */
public class NetworkMetrics implements MeterBinder {

    public static final int MAX_REFRESH_TIME = 5_000;

    public enum NetworkMetric {

        BYTES_RECEIVED,
        BYTES_SENT,
        PACKETS_RECEIVED,
        PACKETS_SENT
    }

    private final Iterable<Tag> tags;
    private final CachedNetworkStats networkStats =
        new CachedNetworkStats(new SystemInfo().getHardware().getNetworkIFs());

    public NetworkMetrics() {
        this(Collections.emptyList());
    }

    public NetworkMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {

        FunctionCounter.builder("system.network.bytes.received",
            NetworkMetric.BYTES_RECEIVED, this::getNetworkMetricAsLong)
            .tags(tags)
            .baseUnit("bytes")
            .register(meterRegistry);

        FunctionCounter.builder("system.network.bytes.sent",
            NetworkMetric.BYTES_SENT, this::getNetworkMetricAsLong)
            .tags(tags)
            .baseUnit("bytes")
            .register(meterRegistry);

        FunctionCounter.builder("system.network.packets.received",
            NetworkMetric.PACKETS_RECEIVED, this::getNetworkMetricAsLong)
            .tags(tags)
            .register(meterRegistry);

        FunctionCounter.builder("system.network.packets.sent",
            NetworkMetric.PACKETS_SENT, this::getNetworkMetricAsLong)
            .tags(tags)
            .register(meterRegistry);
    }

    private long getNetworkMetricAsLong(NetworkMetric type) {
        networkStats.refresh();

        switch (type) {
            case BYTES_SENT: return networkStats.bytesSent;
            case PACKETS_SENT: return networkStats.packetsSent;
            case BYTES_RECEIVED: return networkStats.bytesReceived;
            case PACKETS_RECEIVED: return networkStats.packetsReceived;
            default: throw new IllegalStateException("Unknown NetworkMetrics: " + type.name());
        }
    }

    private static class CachedNetworkStats {

        private final Lock lock = new ReentrantLock();

        private final NetworkIF[] networks;
        private long lastRefresh = 0;

        public volatile long bytesReceived;
        public volatile long bytesSent;
        public volatile long packetsReceived;
        public volatile long packetsSent;

        public CachedNetworkStats(NetworkIF[] networkIFs) {
            this.networks = networkIFs;
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

        private  void doRefresh() {

            if (System.currentTimeMillis() - lastRefresh < MAX_REFRESH_TIME) {
                return;
            }

            long bytesReceived = 0;
            long bytesSent = 0;
            long packetsReceived = 0;
            long packetsSent = 0;

            for (NetworkIF nif : networks) {
                nif.updateNetworkStats();
                bytesReceived += nif.getBytesRecv();
                bytesSent += nif.getBytesSent();
                packetsReceived += nif.getPacketsRecv();
                packetsSent += nif.getPacketsSent();

            }
            this.bytesReceived = bytesReceived;
            this.bytesSent = bytesSent;
            this.packetsReceived = packetsReceived;
            this.packetsSent = packetsSent;

            this.lastRefresh = System.currentTimeMillis();
        }
    }
}
