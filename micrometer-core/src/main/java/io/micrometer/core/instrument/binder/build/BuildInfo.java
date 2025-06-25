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

import java.time.Instant;
import java.util.Optional;

public class BuildInfo {

    private final String group;

    private final String artifact;

    private final String name;

    private final String version;

    private final Instant timestamp;

    private BuildInfo(String group, String artifact, String name, String version, Instant timestamp) {
        this.group = group;
        this.artifact = artifact;
        this.name = name;
        this.version = version;
        this.timestamp = timestamp;
    }

    public static BuildInfo.Builder builder() {
        return new BuildInfo.Builder();
    }

    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    public Optional<String> getArtifact() {
        return Optional.ofNullable(artifact);
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }

    public Optional<Instant> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    public static class Builder {

        private String group;

        private String artifact;

        private String name;

        private String version;

        private Instant timestamp;

        private Builder() {
        }

        public BuildInfo build() {
            return new BuildInfo(group, artifact, name, version, timestamp);
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder artifact(String artifact) {
            this.artifact = artifact;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

    }

}
