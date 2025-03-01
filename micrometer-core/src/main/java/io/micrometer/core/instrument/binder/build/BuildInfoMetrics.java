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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.Instant;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class BuildInfoMetrics implements MeterBinder {

    private final BuildInfo buildInfo;

    private final Iterable<Tag> tags;

    public BuildInfoMetrics(BuildInfo buildInfo) {
        this(buildInfo, emptyList());
    }

    public BuildInfoMetrics(BuildInfo buildInfo, Iterable<Tag> tags) {
        requireNonNull(buildInfo, "buildInfo");
        requireNonNull(tags, "tags");
        this.buildInfo = buildInfo;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        requireNonNull(registry, "registry");
        Gauge.builder("build.info", () -> 1L)
            .description("Build information")
            .strongReference(true)
            .tag("group", buildInfo.getGroup().orElse(DEFAULT_TAG_VALUE))
            .tag("artifact", buildInfo.getArtifact().orElse(DEFAULT_TAG_VALUE))
            .tag("name", buildInfo.getName().orElse(DEFAULT_TAG_VALUE))
            .tag("version", buildInfo.getVersion().orElse(DEFAULT_TAG_VALUE))
            .tag("timestamp", buildInfo.getTimestamp().map(Instant::toString).orElse(DEFAULT_TAG_VALUE))
            .tags(tags)
            .register(registry);
    }

}
