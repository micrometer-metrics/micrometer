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
package io.micrometer.core.instrument.binder.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class DatabaseTableMetricsTest {

    private MeterRegistry registry;

    private JDBCDataSource ds;

    @BeforeEach
    void setup() throws SQLException {
        registry = new SimpleMeterRegistry();

        ds = new JDBCDataSource();
        ds.setURL("jdbc:hsqldb:mem:test");

        // tag::statement[]
        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("CREATE TABLE foo (id int)").execute();
            conn.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate();
        }
        // end::statement[]
    }

    @AfterEach
    void shutdown() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("SHUTDOWN").execute();
        }
    }

    @Test
    void rowCountGauge() {
        // tag::monitor[]
        DatabaseTableMetrics.monitor(registry, "foo", "mydb", ds);
        // end::monitor[]

        // tag::result[]
        assertThat(registry.get("db.table.size").tag("table", "foo").tag("db", "mydb").gauge().value()).isEqualTo(1.0);
        // end::result[]
    }

    @Test
    void rowCountForNonExistentTable() {
        DatabaseTableMetrics.monitor(registry, "dne", "mydb", ds);
        assertThat(registry.get("db.table.size").tag("table", "dne").tag("db", "mydb").gauge().value()).isEqualTo(0.0);
    }

}
