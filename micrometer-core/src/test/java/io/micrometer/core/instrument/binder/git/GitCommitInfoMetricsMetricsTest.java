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

package io.micrometer.core.instrument.binder.git;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitCommitInfoMetricsTest {

    @Test
    void shouldTagAllGitCommitInfoWithValuesWhenPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        GitCommitInfo gitCommitInfo = GitCommitInfo.builder()
            .branch("my-branch")
            .commitId("1234567890123456789012345678901234567890")
            .commitIdShort("12345678")
            .commitTime(LocalDateTime.of(2023, 1, 2, 3, 4, 5).toInstant(UTC))
            .build();

        GitCommitInfoMetrics gitCommitInfoMetrics = new GitCommitInfoMetrics(gitCommitInfo);

        gitCommitInfoMetrics.bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("git.commit.info").gauges();

        assertThat(gauges).hasSize(1);

        Gauge gitCommitInfoGauge = gauges.iterator().next();

        assertThat(gitCommitInfoGauge.value()).isEqualTo(1);

        Meter.Id id = gitCommitInfoGauge.getId();

        assertThat(id.getName()).isEqualTo("git.commit.info");
        assertThat(id.getTags()).containsExactlyInAnyOrder(Tag.of("branch", "my-branch"),
                Tag.of("commit.id", "1234567890123456789012345678901234567890"), Tag.of("commit.id.short", "12345678"),
                Tag.of("commit.timestamp", "2023-01-02T03:04:05Z"));
    }

    @Test
    void shouldTagAllGitCommitInfoWithUnknownWhenNotPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        GitCommitInfo gitCommitInfo = GitCommitInfo.builder().build();

        GitCommitInfoMetrics gitCommitInfoMetrics = new GitCommitInfoMetrics(gitCommitInfo);

        gitCommitInfoMetrics.bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("git.commit.info").gauges();

        assertThat(gauges).hasSize(1);

        Gauge gitCommitInfoGauge = gauges.iterator().next();

        assertThat(gitCommitInfoGauge.value()).isEqualTo(1);

        Meter.Id id = gitCommitInfoGauge.getId();

        assertThat(id.getName()).isEqualTo("git.commit.info");
        assertThat(id.getTags()).containsExactlyInAnyOrder(Tag.of("branch", "unknown"), Tag.of("commit.id", "unknown"),
                Tag.of("commit.id.short", "unknown"), Tag.of("commit.timestamp", "unknown"));
    }

    @Test
    void throwsWhenGitCommitInfoIsNull() {
        assertThatThrownBy(() -> new GitCommitInfoMetrics(null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("gitCommitInfo");
    }

    @Test
    void throwsWhenRegistryIsNull() {
        GitCommitInfo gitCommitInfo = GitCommitInfo.builder().build();

        GitCommitInfoMetrics gitCommitInfoMetrics = new GitCommitInfoMetrics(gitCommitInfo);

        assertThatThrownBy(() -> gitCommitInfoMetrics.bindTo(null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("registry");
    }

}
