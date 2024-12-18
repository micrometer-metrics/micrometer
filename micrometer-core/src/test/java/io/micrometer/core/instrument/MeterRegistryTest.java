/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class MeterRegistryTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void acceptMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return id.getName().contains("jvm") ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        });

        assertThat(registry.counter("jvm.my.counter")).isInstanceOf(NoopCounter.class);
        assertThat(registry.counter("my.counter")).isNotInstanceOf(NoopCounter.class);
    }

    @Test
    void overridingAcceptMeterFilter() {
        registry.config().meterFilter(MeterFilter.accept(m -> m.getName().startsWith("jvm.important")));
        registry.config().meterFilter(MeterFilter.deny(m -> m.getName().startsWith("jvm")));

        assertThat(registry.counter("jvm.my.counter")).isInstanceOf(NoopCounter.class);
        assertThat(registry.counter("jvm.important.counter")).isNotInstanceOf(NoopCounter.class);
        assertThat(registry.counter("my.counter")).isNotInstanceOf(NoopCounter.class);
    }

    @Test
    void idTransformingMeterFilter() {
        registry.config().meterFilter(MeterFilter.ignoreTags("k1"));

        registry.counter("my.counter", "k1", "v1");
        registry.get("my.counter").counter();
        assertThat(registry.find("my.counter").tags("k1", "v1").counter()).isNull();
    }

    @Test
    void histogramConfigTransformingMeterFilter() {
        MeterRegistry registry = new SimpleMeterRegistry() {
            @Override
            protected Timer newTimer(@Nonnull Meter.Id id, DistributionStatisticConfig histogramConfig,
                    PauseDetector pauseDetector) {
                assertThat(histogramConfig.isPublishingHistogram()).isTrue();
                return super.newTimer(id, histogramConfig, pauseDetector);
            }
        };

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id mappedId, DistributionStatisticConfig config) {
                return DistributionStatisticConfig.builder()
                    .percentiles(0.95)
                    .percentilesHistogram(true)
                    .build()
                    .merge(config);
            }
        });

        registry.timer("my.timer");
    }

    @Test
    void noopMetersAfterRegistryClosed() {
        assertThat(registry.timer("my.timer.before")).isNotInstanceOf(NoopTimer.class);
        registry.close();

        assertThat(registry.isClosed()).isTrue();

        assertThat(registry.timer("my.timer.before")).isNotInstanceOf(NoopTimer.class);
        assertThat(registry.timer("my.timer.after")).isInstanceOf(NoopTimer.class);
    }

    @Test
    void removeMeters() {
        registry.counter("my.counter");

        Counter counter = registry.find("my.counter").counter();
        assertThat(counter).isNotNull();

        assertThat(registry.remove(counter)).isSameAs(counter);
        assertThat(registry.find("my.counter").counter()).isNull();
        assertThat(registry.remove(counter)).isNull();
    }

    @Test
    void removeMetersAffectedByMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("another.name");
            }
        });

        Counter counter = registry.counter("name");
        assertThat(registry.find("another.name").counter()).isSameAs(counter);
        assertThat(registry.remove(counter)).isSameAs(counter);
        assertThat(registry.find("another.name").counter()).isNull();
    }

    @Test
    void removeMetersAffectedByNonIdempotentMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("prefix." + id.getName());
            }
        });

        Counter counter = registry.counter("name");
        assertThat(registry.find("prefix.name").counter()).isSameAs(counter);
        assertThat(registry.remove(counter)).isSameAs(counter);
        assertThat(registry.find("prefix.name").counter()).isNull();
    }

    @Test
    void removeMetersWithSynthetics() {
        Timer timer = Timer.builder("my.timer").publishPercentiles(0.95).register(registry);

        assertThat(registry.getMeters()).hasSize(2);
        registry.remove(timer);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void removeMetersWithSyntheticsAffectedByMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("another.name");
            }
        });

        Timer timer = Timer.builder("my.timer").publishPercentiles(0.95).register(registry);

        assertThat(registry.getMeters()).hasSize(2);
        registry.remove(timer);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void clear() {
        registry.counter("my.counter");
        registry.counter("my.counter2");

        assertThat(registry.find("my.counter").counter()).isNotNull();
        assertThat(registry.find("my.counter2").counter()).isNotNull();

        registry.clear();

        assertThat(registry.find("my.counter").counter()).isNull();
        assertThat(registry.find("my.counter2").counter()).isNull();
    }

    @Test
    void gaugeRegistersGaugeOnceAndSubsequentGaugeCallsWillNotRegister() {
        registry.gauge("my.gauge", 1d);
        registry.gauge("my.gauge", 2d);

        assertThat(registry.get("my.gauge").gauge().value()).isEqualTo(1d);
    }

    @Test
    void shouldNotLetRegisteringMetersTwice() {
        registry.counter("my.dupe.meter");
        assertThatThrownBy(() -> registry.timer("my.dupe.meter")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                    "There is already a registered meter of a different type (CumulativeCounter vs. Timer) with the same name: my.dupe.meter")
            .hasNoCause();
    }

    @Test
    @Issue("#4352")
    void baseUnitStringShouldBeCachedAndReturnTheSameInstance() {
        Timer timer1 = registry.timer("test.timer1");
        Timer timer2 = registry.timer("test.timer2");
        assertThat(timer1.getId().getBaseUnit()).isSameAs(timer2.getId().getBaseUnit());
    }

    @Test
    @Issue("#4482")
    void acceptPercentilesNullOrEmpty() {
        LongTaskTimer.builder("timer.percentiles.null").publishPercentiles(null).register(registry);
        LongTaskTimer.builder("timer.percentiles.empty").publishPercentiles(new double[] {}).register(registry);
    }

    @Test
    void removeByPreFilterIdAfterAddingFilterAndDifferentlyMappedId() {
        Counter c1 = registry.counter("counter");
        registry.config().commonTags("common", "tag");
        Counter c2 = registry.counter("counter");

        assertThat(registry.removeByPreFilterId(c1.getId())).isSameAs(c2).isNotSameAs(c1);
        assertThat(registry.getMeters()).containsExactly(c1);
    }

    @Test
    void filterConfiguredAfterMeterRegistered() {
        Counter c1 = registry.counter("counter");
        registry.config().commonTags("common", "tag");
        Counter c2 = registry.counter("counter");

        assertThat(c1.getId().getTags()).isEmpty();
        assertThat(c2.getId().getTags()).containsExactly(Tag.of("common", "tag"));
    }

    @Test
    void doNotCallFiltersWhenUnnecessary() {
        AtomicInteger filterCallCount = new AtomicInteger();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                filterCallCount.incrementAndGet();
                return id;
            }
        });
        Counter c1 = registry.counter("counter");
        assertThat(filterCallCount.get()).isOne();
        Counter c2 = registry.counter("counter");
        assertThat(filterCallCount.get()).isOne();

        assertThat(c1).isSameAs(c2);

        registry.counter("other");
        assertThat(filterCallCount.get()).isEqualTo(2);
    }

    @Test
    void differentPreFilterIdsMapToSameIdWithStaleId() {
        Counter c1 = registry.counter("counter");
        registry.config().meterFilter(MeterFilter.ignoreTags("ignore"));
        Counter c2 = registry.counter("counter", "ignore", "value");

        assertThat(c1).isSameAs(c2);
        assertThat(registry.remove(c1)).isSameAs(c2);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @Issue("#4971")
    void differentPreFilterIdsMapToSameId_thenCacheIsBounded() {
        registry.config().meterFilter(MeterFilter.replaceTagValues("secret", s -> "redacted"));
        Counter c1 = registry.counter("counter", "secret", "value");
        Counter c2 = registry.counter("counter", "secret", "value2");

        assertThat(c1).isSameAs(c2);
        // even though we have 2 different pre-filter IDs, the second should not be added
        // to the map because it would result in a memory leak with a high cardinality tag
        // that's otherwise limited in cardinality by a MeterFilter
        assertThat(registry._getPreFilterIdToMeterMap()).hasSize(1);
        assertThat(registry._getMeterToPreFilterIdMap()).hasSize(1);

        assertThat(registry.remove(c1)).isSameAs(c2);
        assertThat(registry.getMeters()).isEmpty();
        assertThat(registry._getPreFilterIdToMeterMap()).isEmpty();
        assertThat(registry._getMeterToPreFilterIdMap()).isEmpty();
    }

    @Test
    void samePreFilterIdsMapToDifferentIdWithStaleMeter() {
        Counter c1 = registry.counter("counter", "ignore", "value");
        registry.config().meterFilter(MeterFilter.ignoreTags("ignore"));
        Counter c2 = registry.counter("counter", "ignore", "value");

        assertThat(c1).isNotSameAs(c2);
        assertThat(registry.remove(c1)).isNotSameAs(c2);
        Counter c3 = registry.counter("counter", "ignore", "value");
        assertThat(c3).isSameAs(c2);
        assertThat(registry.remove(c2)).isSameAs(c3);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void removingStaleMeterRemovesItFromAllInternalState() {
        registry.config().commonTags("application", "abcservice");
        Counter c1 = registry.counter("counter");
        // make c1 marked as stale
        registry.config().commonTags("common", "tag");
        assertThat(registry._getStalePreFilterIds()).hasSize(1);

        registry.remove(c1.getId());
        assertThat(registry.getMeters()).isEmpty();
        assertThat(registry._getPreFilterIdToMeterMap()).isEmpty();
        assertThat(registry._getMeterToPreFilterIdMap()).isEmpty();
        assertThat(registry._getStalePreFilterIds()).isEmpty();
    }

    @Test
    @Issue("#5035")
    void multiplePreFilterIdsMapToSameId_removeByPreFilterId() {
        registry.config().meterFilter(MeterFilter.replaceTagValues("secret", s -> "redacted"));
        Counter c1 = registry.counter("counter", "secret", "value");
        Counter c2 = registry.counter("counter", "secret", "value2");

        Meter.Id preFilterId = new Meter.Id("counter", Tags.of("secret", "value2"), null, null, Meter.Type.COUNTER);
        assertThat(registry.removeByPreFilterId(preFilterId)).isSameAs(c1).isSameAs(c2);
        assertThat(registry.getMeters()).isEmpty();
        assertThat(registry._getPreFilterIdToMeterMap()).isEmpty();
        assertThat(registry._getMeterToPreFilterIdMap()).isEmpty();
    }

    @Test
    void unchangedStaleMeterShouldBeUnmarked() {
        Counter c1 = registry.counter("counter");
        // make c1 stale
        registry.config().meterFilter(MeterFilter.ignoreTags("abc"));
        assertThat(registry._getStalePreFilterIds()).hasSize(1);
        // this should cause c1 (== c2) to be unmarked as stale
        Counter c2 = registry.counter("counter");

        assertThat(c1).isSameAs(c2);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry._getPreFilterIdToMeterMap()).hasSize(1);
        assertThat(registry._getStalePreFilterIds())
            .describedAs("If the meter-filter doesn't alter the meter creation, meters are never unmarked "
                    + "from staleness and we end up paying the additional cost every time")
            .isEmpty();
    }

}
