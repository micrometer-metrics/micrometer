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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.MicrometerJvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.OtelJvmMetersConventions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassLoaderMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void classLoadingMetrics() {
        new ClassLoaderMetrics().bindTo(registry);

        assertThat(registry.get("jvm.classes.loaded").gauge().value()).isGreaterThan(0);
    }

    @Test
    void extraTagsAreApplied() {
        new ClassLoaderMetrics(Tags.of("extra", "tags")).bindTo(registry);

        assertThat(registry.get("jvm.classes.loaded").tag("extra", "tags").gauge().value()).isGreaterThan(0);
    }

    @Test
    void otelConventions() {
        new ClassLoaderMetrics(new OtelJvmMetersConventions.OtelJvmClassLoadingMeterConventions()).bindTo(registry);

        assertThat(registry.get("jvm.class.loaded").functionCounter().count()).isGreaterThan(0);
        assertThat(registry.get("jvm.class.unloaded").functionCounter().count()).isGreaterThanOrEqualTo(0);
        assertThat(registry.get("jvm.class.count").gauge().value()).isGreaterThan(0);
    }

    @Test
    void otelConventionsWithExtraTags() {
        new ClassLoaderMetrics(
                new OtelJvmMetersConventions.OtelJvmClassLoadingMeterConventions(Tags.of("extra", "tag")))
            .bindTo(registry);

        assertThat(registry.get("jvm.class.loaded").tag("extra", "tag").functionCounter().count()).isGreaterThan(0);
        assertThat(registry.get("jvm.class.unloaded").tag("extra", "tag").functionCounter().count())
            .isGreaterThanOrEqualTo(0);
        assertThat(registry.get("jvm.class.count").tag("extra", "tag").gauge().value()).isGreaterThan(0);
    }

    @Test
    void customConventions() {
        new ClassLoaderMetrics(new MyClassLoaderConventions()).bindTo(registry);

        assertThat(registry.get("class.loaded")
            .tag("common", "custom")
            .tag("specific", "custom")
            .functionCounter()
            .count()).isPositive();
        // overridden common tags applied to conventions not overridden
        assertThat(registry.get("jvm.classes.unloaded").tag("common", "custom").functionCounter().count())
            .isNotNegative();
    }

    static class MyClassLoaderConventions extends MicrometerJvmClassLoadingMeterConventions {

        public MyClassLoaderConventions() {
            super();
        }

        public MyClassLoaderConventions(Tags extraTags) {
            super(extraTags);
        }

        @Override
        protected Tags getCommonTags() {
            return super.getCommonTags().and(Tags.of("common", "custom"));
        }

        @Override
        public MeterConvention<Object> loadedConvention() {
            return new SimpleMeterConvention<>("class.loaded", getCommonTags().and(Tags.of("specific", "custom")));
        }

    }

}
