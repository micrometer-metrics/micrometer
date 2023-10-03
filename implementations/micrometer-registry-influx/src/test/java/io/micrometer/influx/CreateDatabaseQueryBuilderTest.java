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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CreateDatabaseQueryBuilderTest {

    private static final String TEST_DB_NAME = "dummy_database_0";

    /**
     * Class Parameters:
     * <p>
     * - database name - retention clauses
     * <p>
     * Class Criteria - database name is not null -> true false - retention clauses -> no
     * one all
     */
    private final CreateDatabaseQueryBuilder createDatabaseQueryBuilder = new CreateDatabaseQueryBuilder(TEST_DB_NAME);

    @Test
    void noEmptyDatabaseName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CreateDatabaseQueryBuilder(null));
    }

    @Test
    void noRetentionPolicy() {
        String query = createDatabaseQueryBuilder.build();
        assertThat(query).isEqualTo("CREATE DATABASE \"dummy_database_0\"");
    }

    @Test
    void oneClauseInRetentionPolicy() {
        String query = createDatabaseQueryBuilder.setRetentionPolicyName("dummy_policy").build();
        assertThat(query).isEqualTo("CREATE DATABASE \"dummy_database_0\" WITH NAME dummy_policy");
    }

    @Test
    void allClausesInRetentionPolicy() {
        String query = createDatabaseQueryBuilder.setRetentionPolicyName("dummy_policy")
            .setRetentionDuration("2d")
            .setRetentionReplicationFactor(1)
            .setRetentionShardDuration("3")
            .build();
        assertThat(query).isEqualTo(
                "CREATE DATABASE \"dummy_database_0\" WITH DURATION 2d REPLICATION 1 SHARD DURATION 3 NAME dummy_policy");
    }

}
