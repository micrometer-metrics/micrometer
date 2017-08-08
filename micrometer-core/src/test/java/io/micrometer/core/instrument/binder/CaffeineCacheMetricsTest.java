package io.micrometer.core.instrument.binder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static io.micrometer.core.instrument.Meter.Type.Counter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaffeineCacheMetricsTest {


  @Test
  public void cacheExposesMetricsForHitMissAndEviction() throws Exception {
    Cache<String, String> cache = Caffeine.newBuilder().maximumSize(2).recordStats().executor(new Executor() {
      @Override
      public void execute(Runnable command) {
        // Run cleanup in same thread, to remove async behavior with evictions
        command.run();
      }
    }).build();
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    CaffeineCacheMetrics collector = new CaffeineCacheMetrics("users", cache);
    collector.bindTo(registry);

    cache.getIfPresent("user1");
    cache.getIfPresent("user1");
    cache.put("user1", "First User");
    cache.getIfPresent("user1");

    // Add to cache to trigger eviction.
    cache.put("user2", "Second User");
    cache.put("user3", "Third User");
    cache.put("user4", "Fourth User");

    assertMetric(registry, Counter, Tags.zip("cache", "users", "result", "hit"),"users", 1.0, "caffeine_cache_requests");
    assertMetric(registry, Counter, Tags.zip("cache", "users", "result", "miss"),"users", 2.0, "caffeine_cache_requests");
    assertMetric(registry, Counter, "users", 3.0, "caffeine_cache_requests_total");
    assertMetric(registry, Counter, "users", 2.0, "caffeine_cache_evictions_total");
  }


  @SuppressWarnings("unchecked")
  @Test
  public void loadingCacheExposesMetricsForLoadsAndExceptions() throws Exception {
    CacheLoader<String, String> loader = mock(CacheLoader.class);
    when(loader.load(anyString()))
            .thenReturn("First User")
            .thenThrow(new RuntimeException("Seconds time fails"))
            .thenReturn("Third User");

    LoadingCache<String, String> cache = Caffeine.newBuilder().recordStats().build(loader);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    CaffeineCacheMetrics collector = new CaffeineCacheMetrics("loadingusers", cache);
    collector.bindTo(registry);

    cache.get("user1");
    cache.get("user1");
    try {
      cache.get("user2");
    } catch (Exception e) {
      // ignoring.
    }
    cache.get("user3");

    assertMetric(registry, Counter, Tags.zip("cache", "loadingusers", "result", "hit"), "loadingusers", 1.0, "caffeine_cache_requests");
    assertMetric(registry, Counter, Tags.zip("cache", "loadingusers", "result", "miss"),  "loadingusers", 3.0, "caffeine_cache_requests");

    assertMetric(registry, Counter, "loadingusers", 1.0, "caffeine_cache_load_failures_total");
    assertMetric(registry, Counter, "loadingusers", 3.0, "caffeine_cache_load_duration_seconds_count");

    assertMetric(registry, Counter, "loadingusers", 3.0, "caffeine_cache_load_duration_seconds_count");
//    assertMetricGreatThan(registry, Counter,"caffeine_cache_load_duration_seconds_sum", "loadingusers", 0.0);
  }

  private void assertMetric(MeterRegistry registry, Meter.Type type, String cacheName, double value, String name) {
    assertMetric(registry, type, Tags.zip("cache", cacheName), cacheName, value, name);
  }

  private void assertMetric(MeterRegistry registry, Meter.Type type, List<Tag> tags, String cacheName, double value, String name) {
    Optional<Meter> meter = registry.findMeter(type, name, tags);
    if(!meter.isPresent()){
      System.out.println("boom");
    }
    assertThat(meter).as("Meter should be in registry type="+type+" tags="+tags+" metricName=").isPresent();

    assertThat(meter.get().measure().stream().findFirst().map(Measurement::getValue).get()).isEqualTo(value);
  }


//  private void assertMetricGreatThan(MeterRegistry registry,  Meter.Type type, String name, String cacheName, double value) {
//    assertThat(registry.findMeter(type, name, Tags.zip("cache", name)).get().measure())
//            .extracting(Measurement::getValue).
//            .isGreaterThan(value);
//  }

}
