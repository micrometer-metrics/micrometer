/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.binder.db;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.BLOCKS_HITS;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.BLOCKS_READS;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.BUFFERS_BACKEND;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.BUFFERS_CHECKPOINT;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.BUFFERS_CLEAN;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.CHECKPOINTS_REQUESTED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.CHECKPOINTS_TIMED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.CONNECTIONS;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.LOCKS;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.ROWS_DEAD;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.ROWS_DELETED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.ROWS_FETCHED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.ROWS_INSERTED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.ROWS_UPDATED;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.SIZE;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.TEMP_WRITES;
import static io.micrometer.binder.db.PostgreSQLDatabaseMetrics.Names.TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Kristof Depypere
 * @author Markus Dobel
 */
class PostgreSQLDatabaseMetricsTest {
    private static final String DATABASE_NAME = "test";
    private static final String FUNCTIONAL_COUNTER_KEY = "key";
    private final DataSource dataSource = mock(DataSource.class);
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final Tags tags = Tags.of("database", DATABASE_NAME);

    @Test
    void shouldRegisterPostgreSqlMetrics() {
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        get(SIZE).gauge();
        get(CONNECTIONS).gauge();
        get(ROWS_FETCHED).functionCounter();
        get(ROWS_INSERTED).functionCounter();
        get(ROWS_UPDATED).functionCounter();
        get(ROWS_DELETED).functionCounter();
        get(ROWS_DEAD).gauge();

        get(BLOCKS_HITS).functionCounter();
        get(BLOCKS_READS).functionCounter();

        get(TEMP_WRITES).functionCounter();
        get(LOCKS).gauge();

        get(TRANSACTIONS).functionCounter();
        get(CHECKPOINTS_TIMED).functionCounter();
        get(CHECKPOINTS_REQUESTED).functionCounter();

        get(BUFFERS_CHECKPOINT).functionCounter();
        get(BUFFERS_CLEAN).functionCounter();
        get(BUFFERS_BACKEND).functionCounter();
    }

    @Test
    void shouldBridgePgStatReset() {
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 10);

        // first reset
        Double result = metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);

        // then
        assertThat(result).isEqualTo(15);
    }

    @Test
    void shouldBridgeDoublePgStatReset() {
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 10);

        // first reset
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 3);

        // second reset
        Double result = metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 1);

        assertThat(result).isEqualTo(14);
    }

    private RequiredSearch get(final String name) {
        return registry.get(name).tags(tags);
    }
}
