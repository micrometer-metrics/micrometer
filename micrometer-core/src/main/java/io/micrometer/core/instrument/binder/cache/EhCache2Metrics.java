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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.StatisticsGateway;

/**
 * @author Jon Schneider
 */
public class EhCache2Metrics implements MeterBinder {
    private final Ehcache cache;
    private final String name;
    private final Iterable<Tag> tags;

    public EhCache2Metrics(Ehcache cache, String name, Iterable<Tag> tags) {
        this.cache = cache;
        this.name = name;
        this.tags = Tags.concat(tags, "name", cache.getName());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        StatisticsGateway s = cache.getStatistics();

        registry.gauge(registry.createId(name + ".size", Tags.concat(tags, "where", "local"),
            "The number of entries held locally in this cache"),
            cache, c -> c.getStatistics().getSize());
        registry.gauge(registry.createId(name + ".size", Tags.concat(tags, "where", "remote"),
            "The number of entries held remotely in this cache"),
            cache, c -> c.getStatistics().getRemoteSize());

        registry.more().counter(registry.createId(name + ".evictions", tags, "Cache evictions"),
            cache, c -> c.getStatistics().cacheEvictedCount());
        registry.more().counter(registry.createId(name + ".removals", tags, "Cache removals"),
            cache, c -> c.getStatistics().cacheRemoveCount());

        registry.more().counter(registry.createId(name + ".puts", Tags.concat(tags, "result", "added"), "Cache puts resulting in a new key/value pair"),
            cache, c -> c.getStatistics().cachePutAddedCount());
        registry.more().counter(registry.createId(name + ".puts", Tags.concat(tags, "result", "updated"), "Cache puts resulting in an updated value"),
            cache, c -> c.getStatistics().cachePutAddedCount());

        countRequests(registry);
        countCommitTransactions(registry);
        countRollbackTransactions(registry);
        countRecoveryTransactions(registry);

        registry.gauge(registry.createId(name + ".local.offheap.size", tags, "Local off-heap size", "bytes"),
            cache, c -> c.getStatistics().getLocalOffHeapSize());
        registry.gauge(registry.createId(name + ".local.heap.size", tags, "Local heap size", "bytes"),
            cache, c -> c.getStatistics().getLocalHeapSizeInBytes());
        registry.gauge(registry.createId(name + ".local.disk.size", tags, "Local disk size", "bytes"),
            cache, c -> c.getStatistics().getLocalDiskSizeInBytes());
    }

    private void countRequests(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss", "reason", "expired"),
            "The number of times cache lookup methods have not returned a value, due to expiry"),
            cache, c -> c.getStatistics().cacheMissExpiredCount());

        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss", "reason", "notFound"),
            "The number of times cache lookup methods have not returned a value, because the key was not found"),
            cache, c -> c.getStatistics().cacheMissNotFoundCount());

        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "hit"),
            "The number of times cache lookup methods have returned a cached value."),
            cache, c -> c.getStatistics().cacheHitCount());
    }

    private void countCommitTransactions(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "readOnly"),
            "Transaction commits that had a read-only result"),
            cache, c -> c.getStatistics().xaCommitReadOnlyCount());

        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "exception"),
            "Transaction commits that failed"),
            cache, c -> c.getStatistics().xaCommitExceptionCount());

        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "committed"),
            "Transaction commits that failed"),
            cache, c -> c.getStatistics().xaCommitCommittedCount());
    }

    private void countRollbackTransactions(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.rollbacks", Tags.concat(tags, "result", "exception"),
            "Transaction rollbacks that failed"),
            cache, c -> c.getStatistics().xaRollbackExceptionCount());

        registry.more().counter(registry.createId(name + ".xa.rollbacks", Tags.concat(tags, "result", "success"),
            "Transaction rollbacks that failed"),
            cache, c -> c.getStatistics().xaRollbackSuccessCount());
    }

    private void countRecoveryTransactions(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.recoveries", Tags.concat(tags, "result", "nothing"),
            "Recovery transactions that recovered nothing"),
            cache, c -> c.getStatistics().xaRecoveryNothingCount());

        registry.more().counter(registry.createId(name + ".xa.recoveries", Tags.concat(tags, "result", "success"),
            "Successful recovery transaction"),
            cache, c -> c.getStatistics().xaRecoveryRecoveredCount());
    }
}
