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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class EhCache2MetricsTest {
    @Test
    void cacheExposesMetrics() {
        CacheManager cacheManager = CacheManager.newInstance();
        cacheManager.addCache("a");
        Cache c = cacheManager.getCache("a");

        MeterRegistry registry = new SimpleMeterRegistry();
        EhCache2Metrics.monitor(registry, c, "ehcache", emptyList());

        c.put(new Element("k", "v", 1));

        registry.mustFind("ehcache.size").tags("name", "a").gauge();
    }
}
