/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.Objects;

import static java.lang.invoke.MethodType.methodType;

/**
 * Adapter for Hazelcast {@code IMap} class created to provide support for both Hazelcast
 * 3 and Hazelcast 4 at the same time. Dynamically checks which Hazelcast version is on
 * the classpath and resolves the right classes.
 *
 * @implNote Note that {@link MethodHandle} is used, so the performance does not suffer.
 */
class HazelcastIMapAdapter {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(HazelcastIMapAdapter.class);

    private static final Class<?> CLASS_DISTRIBUTED_OBJECT = resolveClass("com.hazelcast.core.DistributedObject");

    private static final Class<?> CLASS_I_MAP = resolveOneOf("com.hazelcast.map.IMap", "com.hazelcast.core.IMap");

    private static final Class<?> CLASS_LOCAL_MAP = resolveOneOf("com.hazelcast.map.LocalMapStats",
            "com.hazelcast.monitor.LocalMapStats");

    private static final Class<?> CLASS_NEAR_CACHE_STATS = resolveOneOf("com.hazelcast.nearcache.NearCacheStats",
            "com.hazelcast.monitor.NearCacheStats");

    // Internal Hazelcast classes used only to read MapConfig#isStatisticsEnabled. The
    // package moved from com.hazelcast.spi (3.x) to com.hazelcast.spi.impl (4.x+). Any
    // resolution failure falls back to assuming statistics are enabled.
    private static final @Nullable Class<?> CLASS_ABSTRACT_DISTRIBUTED_OBJECT = resolveOneOfOrNull(
            "com.hazelcast.spi.impl.AbstractDistributedObject", "com.hazelcast.spi.AbstractDistributedObject");

    private static final @Nullable Class<?> CLASS_NODE_ENGINE = resolveOneOfOrNull("com.hazelcast.spi.impl.NodeEngine",
            "com.hazelcast.spi.NodeEngine");

    private static final @Nullable Class<?> CLASS_CONFIG = resolveClassOrNull("com.hazelcast.config.Config");

    private static final @Nullable Class<?> CLASS_MAP_CONFIG = resolveClassOrNull("com.hazelcast.config.MapConfig");

    private static final MethodHandle GET_NAME;

    private static final MethodHandle GET_LOCAL_MAP_STATS;

    private static final @Nullable MethodHandle GET_NODE_ENGINE;

    private static final @Nullable MethodHandle NODE_ENGINE_GET_CONFIG;

    private static final @Nullable MethodHandle CONFIG_GET_MAP_CONFIG;

    private static final @Nullable MethodHandle MAP_CONFIG_IS_STATISTICS_ENABLED;

    static {
        GET_NAME = resolveMethod(CLASS_DISTRIBUTED_OBJECT, "getName", methodType(String.class));
        GET_LOCAL_MAP_STATS = resolveMethod(CLASS_I_MAP, "getLocalMapStats", methodType(CLASS_LOCAL_MAP));
        GET_NODE_ENGINE = resolveMethodOrNull(CLASS_ABSTRACT_DISTRIBUTED_OBJECT, "getNodeEngine", CLASS_NODE_ENGINE);
        NODE_ENGINE_GET_CONFIG = resolveMethodOrNull(CLASS_NODE_ENGINE, "getConfig", CLASS_CONFIG);
        CONFIG_GET_MAP_CONFIG = resolveMethodOrNull(CLASS_CONFIG, "getMapConfig", CLASS_MAP_CONFIG, String.class);
        MAP_CONFIG_IS_STATISTICS_ENABLED = resolveMethodOrNull(CLASS_MAP_CONFIG, "isStatisticsEnabled", boolean.class);
    }

    private final WeakReference<Object> cache;

    HazelcastIMapAdapter(Object cache) {
        this.cache = new WeakReference<>(cache);
    }

    static String nameOf(Object cache) {
        try {
            return (String) GET_NAME.invoke(cache);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Nullable LocalMapStats getLocalMapStats() {
        Object ref = cache.get();
        if (ref == null) {
            return null;
        }

        Object result = invoke(GET_LOCAL_MAP_STATS, ref);
        return result == null ? null : new LocalMapStats(result);
    }

    // Captured once by HazelcastCacheMetrics at construction; runtime toggles
    // via MapConfig#setStatisticsEnabled(...) are not tracked.
    // Rebind the metrics if statistics are enabled later.
    boolean isStatisticsEnabled(String name) {
        Object ref = cache.get();
        if (ref == null || GET_NODE_ENGINE == null || NODE_ENGINE_GET_CONFIG == null || CONFIG_GET_MAP_CONFIG == null
                || MAP_CONFIG_IS_STATISTICS_ENABLED == null) {
            return true;
        }

        try {
            Object nodeEngine = GET_NODE_ENGINE.invoke(ref);
            if (nodeEngine == null) {
                return true;
            }
            Object config = NODE_ENGINE_GET_CONFIG.invoke(nodeEngine);
            if (config == null) {
                return true;
            }
            Object mapConfig = CONFIG_GET_MAP_CONFIG.invoke(config, name);
            if (mapConfig == null) {
                return true;
            }
            return (boolean) MAP_CONFIG_IS_STATISTICS_ENABLED.invoke(mapConfig);
        }
        catch (Throwable t) {
            log.debug("Failed to detect Hazelcast statistics-enabled state, assuming enabled", t);
            return true;
        }
    }

    static class LocalMapStats {

        private static final @Nullable MethodHandle GET_NEAR_CACHE_STATS;

        private static final @Nullable MethodHandle GET_OWNED_ENTRY_COUNT;

        private static final @Nullable MethodHandle GET_HITS;

        private static final @Nullable MethodHandle GET_PUT_OPERATION_COUNT;

        private static final @Nullable MethodHandle GET_SET_OPERATION_COUNT;

        private static final @Nullable MethodHandle GET_BACKUP_ENTRY_COUNT;

        private static final @Nullable MethodHandle GET_BACKUP_ENTRY_MEMORY_COST;

        private static final @Nullable MethodHandle GET_OWNED_ENTRY_MEMORY_COST;

        private static final @Nullable MethodHandle GET_GET_OPERATION_COUNT;

        private static final @Nullable MethodHandle GET_TOTAL_GET_LATENCY;

        private static final @Nullable MethodHandle GET_TOTAL_PUT_LATENCY;

        private static final @Nullable MethodHandle GET_REMOVE_OPERATION_COUNT;

        private static final @Nullable MethodHandle GET_TOTAL_REMOVE_LATENCY;

        static {
            GET_NEAR_CACHE_STATS = resolveMethod("getNearCacheStats", methodType(CLASS_NEAR_CACHE_STATS));

            GET_OWNED_ENTRY_COUNT = resolveMethod("getOwnedEntryCount", methodType(long.class));
            GET_HITS = resolveMethod("getHits", methodType(long.class));
            GET_PUT_OPERATION_COUNT = resolveMethod("getPutOperationCount", methodType(long.class));
            GET_SET_OPERATION_COUNT = resolveMethod("getSetOperationCount", methodType(long.class));
            GET_BACKUP_ENTRY_COUNT = resolveMethod("getBackupEntryCount", methodType(long.class));
            GET_BACKUP_ENTRY_MEMORY_COST = resolveMethod("getBackupEntryMemoryCost", methodType(long.class));
            GET_OWNED_ENTRY_MEMORY_COST = resolveMethod("getOwnedEntryMemoryCost", methodType(long.class));
            GET_GET_OPERATION_COUNT = resolveMethod("getGetOperationCount", methodType(long.class));
            GET_TOTAL_GET_LATENCY = resolveMethod("getTotalGetLatency", methodType(long.class));
            GET_TOTAL_PUT_LATENCY = resolveMethod("getTotalPutLatency", methodType(long.class));
            GET_REMOVE_OPERATION_COUNT = resolveMethod("getRemoveOperationCount", methodType(long.class));
            GET_TOTAL_REMOVE_LATENCY = resolveMethod("getTotalRemoveLatency", methodType(long.class));
        }

        private final Object localMapStats;

        LocalMapStats(Object localMapStats) {
            this.localMapStats = localMapStats;
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getOwnedEntryCount() {
            return (long) invoke(GET_OWNED_ENTRY_COUNT, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getHits() {
            return (long) invoke(GET_HITS, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getPutOperationCount() {
            return (long) invoke(GET_PUT_OPERATION_COUNT, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getSetOperationCount() {
            if (GET_SET_OPERATION_COUNT == null) {
                return 0L;
            }

            return (long) invoke(GET_SET_OPERATION_COUNT, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        double getBackupEntryCount() {
            return (long) invoke(GET_BACKUP_ENTRY_COUNT, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getBackupEntryMemoryCost() {
            return (long) invoke(GET_BACKUP_ENTRY_MEMORY_COST, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getOwnedEntryMemoryCost() {
            return (long) invoke(GET_OWNED_ENTRY_MEMORY_COST, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getGetOperationCount() {
            return (long) invoke(GET_GET_OPERATION_COUNT, localMapStats);
        }

        @Nullable NearCacheStats getNearCacheStats() {
            Object result = invoke(GET_NEAR_CACHE_STATS, localMapStats);
            return result == null ? null : new NearCacheStats(result);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getTotalGetLatency() {
            return (long) invoke(GET_TOTAL_GET_LATENCY, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getTotalPutLatency() {
            return (long) invoke(GET_TOTAL_PUT_LATENCY, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getRemoveOperationCount() {
            return (long) invoke(GET_REMOVE_OPERATION_COUNT, localMapStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getTotalRemoveLatency() {
            return (long) invoke(GET_TOTAL_REMOVE_LATENCY, localMapStats);
        }

        private static @Nullable MethodHandle resolveMethod(String name, MethodType mt) {
            try {
                return MethodHandles.publicLookup().findVirtual(CLASS_LOCAL_MAP, name, mt);
            }
            catch (NoSuchMethodException | IllegalAccessException e) {
                log.debug("Failed to resolve method: " + name, e);
                return null;
            }
        }

    }

    static class NearCacheStats {

        private static final MethodHandle GET_HITS;

        private static final MethodHandle GET_MISSES;

        private static final MethodHandle GET_EVICTIONS;

        private static final MethodHandle GET_PERSISTENCE_COUNT;

        static {
            GET_HITS = resolveMethod("getHits", methodType(long.class));
            GET_MISSES = resolveMethod("getMisses", methodType(long.class));
            GET_EVICTIONS = resolveMethod("getEvictions", methodType(long.class));
            GET_PERSISTENCE_COUNT = resolveMethod("getPersistenceCount", methodType(long.class));
        }

        private Object nearCacheStats;

        NearCacheStats(Object nearCacheStats) {
            this.nearCacheStats = nearCacheStats;
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getHits() {
            return (long) invoke(GET_HITS, nearCacheStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getMisses() {
            return (long) invoke(GET_MISSES, nearCacheStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getEvictions() {
            return (long) invoke(GET_EVICTIONS, nearCacheStats);
        }

        @SuppressWarnings("NullAway")
        // return type of the MethodHandle is long
        long getPersistenceCount() {
            return (long) invoke(GET_PERSISTENCE_COUNT, nearCacheStats);
        }

        private static MethodHandle resolveMethod(String name, MethodType mt) {
            try {
                return MethodHandles.publicLookup().findVirtual(CLASS_NEAR_CACHE_STATS, name, mt);
            }
            catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    private static MethodHandle resolveMethod(Class<?> clazz, String name, MethodType mt) {
        try {
            return MethodHandles.publicLookup().findVirtual(clazz, name, mt);
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> resolveOneOf(String class1, String class2) {
        try {
            return Class.forName(class1);
        }
        catch (ClassNotFoundException e) {
            return resolveClass(class2);
        }
    }

    private static Class<?> resolveClass(String clazz) {
        try {
            return Class.forName(clazz);
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable Class<?> resolveClassOrNull(String clazz) {
        try {
            return Class.forName(clazz);
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static @Nullable Class<?> resolveOneOfOrNull(String class1, String class2) {
        Class<?> resolved = resolveClassOrNull(class1);
        return resolved != null ? resolved : resolveClassOrNull(class2);
    }

    private static @Nullable MethodHandle resolveMethodOrNull(@Nullable Class<?> clazz, String name,
            @Nullable Class<?> returnType, Class<?>... parameterTypes) {
        if (clazz == null || returnType == null) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().findVirtual(clazz, name, methodType(returnType, parameterTypes));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            log.debug("Failed to resolve method: " + name, e);
            return null;
        }
    }

    private static @Nullable Object invoke(@Nullable MethodHandle mh, Object object) {
        try {
            return Objects.requireNonNull(mh).invoke(object);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
