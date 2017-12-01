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
package io.micrometer.spring.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentMapCacheMetricsTest {
    @Test
    public void gaugeCacheSize() {
        ConcurrentMapCache cache = new ConcurrentMapCache("a");
        cache.put("k", "v");

        MeterRegistry registry = new SimpleMeterRegistry();
        new ConcurrentMapCacheMetrics(cache, "spring.cache", emptyList()).bindTo(registry);

        assertThat(registry.mustFind("spring.cache.size").tags("name", "a")
            .gauge().value()).isEqualTo(1.0);
    }
}
