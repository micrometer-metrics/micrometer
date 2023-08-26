/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.binder.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extends {@link AbstractCacheMetricsTest} to specifically test behaviors like metric
 * name prefixes for cache metrics.
 */
public class CacheMetricsBehaviorTest extends AbstractCacheMetricsTest {

    /**
     * Cache instance for testing. Initialized using Guava's CacheBuilder.
     */
    private Cache<String, String> cache = CacheBuilder.newBuilder().build();

    /**
     * Custom binder instance for linking cache metrics to a {@link MeterRegistry}. Uses a
     * specialized subclass to enable behavior testing.
     */
    private BehaviorTestCacheMeterBinder<String, String, Cache<String, String>> metrics;

    /**
     * A concrete class that extends {@link CacheMeterBinder} and overrides behavior for
     * testing purposes, such as allowing custom metric name prefixes.
     *
     * @param <K> Type of the keys in the cache.
     * @param <V> Type of the values in the cache.
     * @param <C> Type of the cache.
     */
    private static class BehaviorTestCacheMeterBinder<K, V, C extends Cache<K, V>> extends CacheMeterBinder<C> {

        /**
         * Optional metric name prefix.
         */
        private String metricNamePrefix;

        /**
         * Default constructor.
         * @param cache The cache object to bind metrics for.
         * @param name The name of the cache.
         * @param tags Any additional tags to include in metrics.
         */
        public BehaviorTestCacheMeterBinder(C cache, String name, Iterable<Tag> tags) {
            super(cache, name, tags);
        }

        @Override
        protected String getMetricNamePrefix() {
            return metricNamePrefix;
        }

        /**
         * Setter for the custom metric name prefix.
         * @param metricNamePrefix Custom metric name prefix.
         */
        public void setMetricNamePrefix(String metricNamePrefix) {
            this.metricNamePrefix = metricNamePrefix;
        }

        private long TEST_VALUE = 2L;

        @Override
        protected Long size() {
            return TEST_VALUE;
        }

        @Override
        protected long hitCount() {
            return TEST_VALUE;
        }

        @Override
        protected Long missCount() {
            return TEST_VALUE;
        }

        @Override
        protected Long evictionCount() {
            return TEST_VALUE;
        }

        @Override
        protected long putCount() {
            return TEST_VALUE;
        }

        @Override
        protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
            // Intentionally left empty for testing purposes
        }

    }

    /**
     * Verifies that metric name prefixes work as expected.
     */
    @Test
    void testPrefixOnMetrics() {
        String testPrefix = "metric.name.prefix.test.";

        // Given: Set up the test environment
        metrics = new BehaviorTestCacheMeterBinder<>(cache, "testCache", expectedTag);
        metrics.setMetricNamePrefix(testPrefix);
        MeterRegistry registry = new SimpleMeterRegistry();

        // When: Metrics are bound to the registry
        metrics.bindTo(registry);

        // Then: Verify that the custom metric name prefix is applied correctly
        verifyCommonCacheMetricsHavePrefixInTheName(registry, metrics, testPrefix);
    }

    /**
     * Verifies that common cache metrics appear with the correct name prefix in the
     * {@link MeterRegistry}. This function checks for metrics like 'cache.puts',
     * 'cache.gets', etc., but with the applied prefix.
     * @param meterRegistry The meter registry containing the published metrics.
     * @param metrics The custom CacheMeterBinder used for metric binding.
     * @param prefix The expected metric name prefix.
     */
    private void verifyCommonCacheMetricsHavePrefixInTheName(MeterRegistry meterRegistry,
            BehaviorTestCacheMeterBinder metrics, String prefix) {

        FunctionCounter missCount = fetch(meterRegistry, prefix + "cache.gets", Tags.of("result", "miss"))
            .functionCounter();
        assertThat(missCount.count()).isEqualTo(metrics.missCount().doubleValue());

        FunctionCounter hitCount = fetch(meterRegistry, prefix + "cache.gets", Tags.of("result", "hit"))
            .functionCounter();
        assertThat(hitCount.count()).isEqualTo(Double.valueOf(metrics.hitCount()));

        FunctionCounter cachePuts = fetch(meterRegistry, prefix + "cache.puts").functionCounter();
        assertThat(cachePuts.count()).isEqualTo(Double.valueOf(metrics.putCount()));

        FunctionCounter cacheEvictions = fetch(meterRegistry, prefix + "cache.evictions").functionCounter();
        assertThat(cacheEvictions.count()).isEqualTo(Double.valueOf(metrics.evictionCount()));

        Gauge cacheSize = fetch(meterRegistry, prefix + "cache.size").gauge();
        assertThat(cacheSize.value()).isEqualTo(Double.valueOf(metrics.size()));
    }

}
