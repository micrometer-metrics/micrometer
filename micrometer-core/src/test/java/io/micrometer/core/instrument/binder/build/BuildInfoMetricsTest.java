/*
 * Copyright 2023 VMware, Inc.
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

package io.micrometer.core.instrument.binder.build;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildInfoMetricsTest {

    @Test
    void shouldTagBuildInfoWithValuesWhenPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        BuildInfo buildInfo = BuildInfo.builder()
            .group("my-group")
            .artifact("my-artifact")
            .name("my-name")
            .version("0.0.0-version")
            .timestamp(LocalDateTime.of(2023, 1, 2, 3, 4, 5).toInstant(UTC))
            .build();

        BuildInfoMetrics buildInfoMetrics = new BuildInfoMetrics(buildInfo);

        buildInfoMetrics.bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("build.info").gauges();

        assertThat(gauges).hasSize(1);

        Gauge buildInfoGauge = gauges.iterator().next();

        assertThat(buildInfoGauge.value()).isEqualTo(1);

        Meter.Id id = buildInfoGauge.getId();

        assertThat(id.getName()).isEqualTo("build.info");
        assertThat(id.getTags()).containsExactlyInAnyOrder(Tag.of("group", "my-group"),
                Tag.of("artifact", "my-artifact"), Tag.of("name", "my-name"), Tag.of("version", "0.0.0-version"),
                Tag.of("timestamp", "2023-01-02T03:04:05Z"));
    }

    @Test
    void shouldTagBuildInfoWithValuesAndExtraTagsWhenPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        BuildInfo buildInfo = BuildInfo.builder()
            .group("my-group")
            .artifact("my-artifact")
            .name("my-name")
            .version("0.0.0-version")
            .timestamp(LocalDateTime.of(2023, 1, 2, 3, 4, 5).toInstant(UTC))
            .build();
        List<Tag> tags = List.of(Tag.of("custom-tag", "custom-tag-value"));

        BuildInfoMetrics buildInfoMetrics = new BuildInfoMetrics(buildInfo, tags);

        buildInfoMetrics.bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("build.info").gauges();

        assertThat(gauges).hasSize(1);

        Gauge buildInfoGauge = gauges.iterator().next();

        assertThat(buildInfoGauge.value()).isEqualTo(1);

        Meter.Id id = buildInfoGauge.getId();

        assertThat(id.getName()).isEqualTo("build.info");
        assertThat(id.getTags()).containsExactlyInAnyOrder(Tag.of("group", "my-group"),
                Tag.of("artifact", "my-artifact"), Tag.of("name", "my-name"), Tag.of("version", "0.0.0-version"),
                Tag.of("timestamp", "2023-01-02T03:04:05Z"), Tag.of("custom-tag", "custom-tag-value"));
    }

    @Test
    void shouldTagAllBuildInfoWithUnknownWhenNotPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        BuildInfo buildInfo = BuildInfo.builder().build();

        BuildInfoMetrics buildInfoMetrics = new BuildInfoMetrics(buildInfo);

        buildInfoMetrics.bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("build.info").gauges();

        assertThat(gauges).hasSize(1);

        Gauge buildInfoGauge = gauges.iterator().next();

        assertThat(buildInfoGauge.value()).isEqualTo(1);

        Meter.Id id = buildInfoGauge.getId();

        assertThat(id.getName()).isEqualTo("build.info");
        assertThat(id.getTags()).containsExactlyInAnyOrder(Tag.of("group", "unknown"), Tag.of("artifact", "unknown"),
                Tag.of("name", "unknown"), Tag.of("version", "unknown"), Tag.of("timestamp", "unknown"));
    }

    @Test
    void throwsWhenRegistryIsNull() {
        BuildInfo buildInfo = BuildInfo.builder().build();

        BuildInfoMetrics buildInfoMetrics = new BuildInfoMetrics(buildInfo);

        assertThatThrownBy(() -> buildInfoMetrics.bindTo(null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("registry");
    }

    @Test
    void throwsWhenBuildInfoIsNullForBuildInfoConstructor() {
        assertThatThrownBy(() -> new BuildInfoMetrics(null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("buildInfo");
    }

    @Test
    void throwsWhenBuildInfoIsNullForBuildInfoAndTagsConstructor() {
        assertThatThrownBy(() -> new BuildInfoMetrics(null, emptyList())).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("buildInfo");
    }

    @Test
    void throwsWhenTagsIsNullForBuildInfoAndTagsConstructor() {
        BuildInfo buildInfo = BuildInfo.builder().build();

        assertThatThrownBy(() -> new BuildInfoMetrics(buildInfo, null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tags");
    }

}
