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

import java.time.Instant;
import java.util.Optional;

/**
 * Information from a Git commit.
 */
public class GitCommitInfo {

    private final String branch;

    private final String commitId;

    private final String commitIdShort;

    private final Instant commitTime;

    private GitCommitInfo(String branch, String commitId, String commitIdShort, Instant commitTime) {
        this.branch = branch;
        this.commitId = commitId;
        this.commitIdShort = commitIdShort;
        this.commitTime = commitTime;
    }

    public static GitCommitInfo.Builder builder() {
        return new GitCommitInfo.Builder();
    }

    public Optional<String> getBranch() {
        return Optional.ofNullable(branch);
    }

    public Optional<String> getCommitId() {
        return Optional.ofNullable(commitId);
    }

    public Optional<String> getShortCommitId() {
        return Optional.ofNullable(commitIdShort);
    }

    public Optional<Instant> getCommitTime() {
        return Optional.ofNullable(commitTime);
    }

    public static class Builder {

        private String branch;

        private String commitId;

        private String commitIdShort;

        private Instant commitTime;

        private Builder() {
        }

        public GitCommitInfo build() {
            return new GitCommitInfo(branch, commitId, commitIdShort, commitTime);
        }

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder commitId(String commitId) {
            this.commitId = commitId;
            return this;
        }

        public Builder commitIdShort(String commitIdShort) {
            this.commitIdShort = commitIdShort;
            return this;
        }

        public Builder commitTime(Instant commitTime) {
            this.commitTime = commitTime;
            return this;
        }

    }

}
