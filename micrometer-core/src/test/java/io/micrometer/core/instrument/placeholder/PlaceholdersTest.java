package io.micrometer.core.instrument.placeholder;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static io.micrometer.core.instrument.placeholder.PlaceholdersTest.MeterAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class PlaceholdersTest {

    private final DropwizardMeterRegistry hierarchicalRegistry = createDropwizardRegistry();

    private final NamingConventionMeterRegistry nonHierarchicalRegistry =
            new NamingConventionMeterRegistry(NamingConvention.snakeCase);

    @Test
    void placeholdersAreRemovedInNonHierarchicalDelegateRegistries() {
        // given
        Placeholders.bindTo(nonHierarchicalRegistry);

        // when
        nonHierarchicalRegistry.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(any(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(any(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    @Test
    void placeholdersAreResolvedWithTagsInHierarchicalDelegateRegistries() {
        // given
        Placeholders.bindTo(hierarchicalRegistry);

        // when
        hierarchicalRegistry.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(any(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .containsKey("metric.mongo.save.count");
    }

    @Test
    void placeholdersAreNotResolvedIfBoundToCompositeRegistryButNotToChildRegistries() {
        // given
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(hierarchicalRegistry);
        composite.add(nonHierarchicalRegistry);

        Placeholders.bindTo(composite);

        // when
        composite.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(any(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .doesNotContainKeys("metric.mongo.save.count")
                .containsKey("metric{db}{op}Count.db.mongo.op.save");

        assertThat(any(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(any(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_{db}_{op}_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    @Test
    void placeholdersAreResolvedIfBoundToCompositeRegistryAndChildRegistries() {
        // given
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(hierarchicalRegistry);
        composite.add(nonHierarchicalRegistry);

        Placeholders.bindTo(composite, hierarchicalRegistry, nonHierarchicalRegistry);

        // when
        composite.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(any(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .containsKey("metric.mongo.save.count");

        assertThat(any(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(any(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    private <T> T any(Collection<T> meters) {
        return meters.iterator().next();
    }

    private DropwizardMeterRegistry createDropwizardRegistry() {
        DropwizardConfig config = new DropwizardConfig() {
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }
        };

        return new DropwizardMeterRegistry(
                config, new MetricRegistry(), HierarchicalNameMapper.DEFAULT, new MockClock()) {
            @Override
            protected Double nullGaugeValue() {
                return Double.NaN;
            }
        };
    }

    static class MeterAssert extends AbstractAssert<MeterAssert, Meter> {

        private MeterAssert(Meter meter, Class<?> selfType) {
            super(meter, selfType);
        }

        static MeterAssert assertThat(Meter actual) {
            return new MeterAssert(actual, MeterAssert.class);
        }

        MeterAssert hasName(String expected) {
            Assertions.assertThat(actual.getId().getName()).isEqualTo(expected);
            return myself;
        }

        MeterAssert hasExactTags(Tag... tags) {
            Assertions.assertThat(actual.getId().getTags()).containsExactly(tags);
            return myself;
        }
    }
}