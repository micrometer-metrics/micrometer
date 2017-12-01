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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.jsr107.ri.spi.RICachingProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static javax.cache.expiry.Duration.ONE_HOUR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class JCacheMetricsTest {
    @ParameterizedTest
    @MethodSource("cachingProviders")
    void cacheExposesMetrics(CachingProvider provider) {
        CacheManager cacheManager = provider.getCacheManager();

        MutableConfiguration<Integer, String> configuration = new MutableConfiguration<>();
        configuration.setTypes(Integer.class, String.class)
            .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR))
            .setStatisticsEnabled(true);

        Cache<Integer, String> cache = cacheManager.createCache("a", configuration);
        cache.put(1, "test");

        MeterRegistry registry = new SimpleMeterRegistry();
        JCacheMetrics.monitor(registry, cache, "jcache", emptyList());

        assertThat(registry.find("jcache.puts").tags("name", "a").gauge().map(Gauge::value)).hasValue(1.0);
    }

    private static Stream<CachingProvider> cachingProviders() {
        return Stream.of(
            new RICachingProvider(),
            new EhcacheCachingProvider()
        );
    }
}
