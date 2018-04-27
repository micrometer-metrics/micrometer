/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.jdbc.db;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;

import javax.sql.DataSource;

/**
 * @author Kristof Depypere
 */
public class PostgreSQLDatabaseMetricsTest {

    public static final String DATABASE_NAME = "test";
    private DataSource dataSource = new HikariDataSource();
    private MeterRegistry registry = new SimpleMeterRegistry();


    @Test
    public void shouldRegisterPostesMetrics() {
        PostgreSQLDatabaseMetrics postgreSQLDatabaseMetrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        postgreSQLDatabaseMetrics.bindTo(registry);


        assertPostgreSQLDatabaseMetrics(DATABASE_NAME);
    }

    private void assertPostgreSQLDatabaseMetrics(String name) {
        registry.get("postgres.size").tag("database", DATABASE_NAME).gauge();
        registry.get("postgres.connections").tag("database", DATABASE_NAME).gauge();
        registry.get("postgres.rows.fetched").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.rows.inserted").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.rows.updated").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.rows.deleted").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.rows.dead").tag("database", DATABASE_NAME).gauge();
        registry.get("postgres.hitratio").tag("database", DATABASE_NAME).gauge();

        registry.get("postgres.transactions").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.checkpoints.timed").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.checkpoints.req").tag("database", DATABASE_NAME).functionCounter();
        registry.get("postgres.checkpoints.bufferratio").tag("database", DATABASE_NAME).gauge();

    }
}
