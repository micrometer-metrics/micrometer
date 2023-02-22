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
package io.micrometer.influx;

import io.micrometer.common.lang.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Builds a create database query for influxdb. It is supposed to be of the following
 * structure:
 * <p>
 * CREATE DATABASE <database_name> [WITH [DURATION <duration>] [REPLICATION <n>] [SHARD
 * DURATION <duration>] [NAME <retention-policy-name>]]
 *
 * @author Vladyslav Oleniuk (vlad.oleniuk@gmail.com)
 */
class CreateDatabaseQueryBuilder {

    private static final String QUERY_MANDATORY_TEMPLATE = "CREATE DATABASE \"%s\"";

    private static final String RETENTION_POLICY_INTRODUCTION = " WITH";

    private static final String DURATION_CLAUSE_TEMPLATE = " DURATION %s";

    private static final String REPLICATION_FACTOR_CLAUSE_TEMPLATE = " REPLICATION %d";

    private static final String SHARD_DURATION_CLAUSE_TEMPLATE = " SHARD DURATION %s";

    private static final String NAME_CLAUSE_TEMPLATE = " NAME %s";

    private final String databaseName;

    private final String[] retentionPolicyClauses = new String[4];

    CreateDatabaseQueryBuilder(String databaseName) {
        if (isEmpty(databaseName)) {
            throw new IllegalArgumentException("The database name cannot be null or empty");
        }
        this.databaseName = databaseName;
    }

    CreateDatabaseQueryBuilder setRetentionDuration(@Nullable String retentionDuration) {
        if (!isEmpty(retentionDuration)) {
            retentionPolicyClauses[0] = String.format(DURATION_CLAUSE_TEMPLATE, retentionDuration);
        }
        return this;
    }

    CreateDatabaseQueryBuilder setRetentionReplicationFactor(@Nullable Integer retentionReplicationFactor) {
        if (retentionReplicationFactor != null) {
            retentionPolicyClauses[1] = String.format(REPLICATION_FACTOR_CLAUSE_TEMPLATE, retentionReplicationFactor);
        }
        return this;
    }

    CreateDatabaseQueryBuilder setRetentionShardDuration(@Nullable String retentionShardDuration) {
        if (!isEmpty(retentionShardDuration)) {
            retentionPolicyClauses[2] = String.format(SHARD_DURATION_CLAUSE_TEMPLATE, retentionShardDuration);
        }
        return this;
    }

    CreateDatabaseQueryBuilder setRetentionPolicyName(@Nullable String retentionPolicyName) {
        if (!isEmpty(retentionPolicyName)) {
            retentionPolicyClauses[3] = String.format(NAME_CLAUSE_TEMPLATE, retentionPolicyName);
        }
        return this;
    }

    String build() {
        StringBuilder queryStringBuilder = new StringBuilder(String.format(QUERY_MANDATORY_TEMPLATE, databaseName));
        if (hasAnyRetentionPolicy()) {
            String retentionPolicyClause = Stream.of(retentionPolicyClauses)
                .filter(Objects::nonNull)
                .reduce(RETENTION_POLICY_INTRODUCTION, String::concat);
            queryStringBuilder.append(retentionPolicyClause);
        }
        return queryStringBuilder.toString();
    }

    private boolean hasAnyRetentionPolicy() {
        return Stream.of(retentionPolicyClauses).anyMatch(Objects::nonNull);
    }

    private boolean isEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }

}
