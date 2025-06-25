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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public class GitCommitInfoMetrics implements MeterBinder {

    private final GitCommitInfo gitCommitInfo;

    public GitCommitInfoMetrics(GitCommitInfo gitCommitInfo) {
        requireNonNull(gitCommitInfo, "gitCommitInfo");
        this.gitCommitInfo = gitCommitInfo;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        requireNonNull(registry, "registry");
        Gauge.builder("git.commit.info", () -> 1L)
            .description("Git commit information")
            .strongReference(true)
            .tag("branch", gitCommitInfo.getBranch().orElse(DEFAULT_TAG_VALUE))
            .tag("commit.id", gitCommitInfo.getCommitId().orElse(DEFAULT_TAG_VALUE))
            .tag("commit.id.short", gitCommitInfo.getShortCommitId().orElse(DEFAULT_TAG_VALUE))
            .tag("commit.timestamp", gitCommitInfo.getCommitTime().map(Instant::toString).orElse(DEFAULT_TAG_VALUE))
            .register(registry);
    }

}
