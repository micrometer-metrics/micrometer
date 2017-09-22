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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * @author Jon Schneider
 */
public class ConcurrentMapCacheMetrics implements MeterBinder {
    private final ConcurrentMapCache cache;
    private final String name;
    private final Iterable<Tag> tags;

    public ConcurrentMapCacheMetrics(ConcurrentMapCache cache, String name, Iterable<Tag> tags) {
        this.cache = cache;
        this.name = name;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(name + ".size", Tags.concat(tags, "name", cache.getName()), cache, c -> c.getNativeCache().size());
    }
}
