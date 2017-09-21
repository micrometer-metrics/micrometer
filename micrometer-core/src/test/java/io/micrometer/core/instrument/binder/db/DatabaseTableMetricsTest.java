package io.micrometer.core.instrument.binder.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
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

        try(Connection conn = ds.getConnection()) {
            conn.prepareStatement("CREATE TABLE foo (id int)").execute();
            conn.prepareStatement("INSERT INTO foo VALUES (1)").executeUpdate();
        }
    }

    @AfterEach
    void shutdown() throws SQLException {
        try(Connection conn = ds.getConnection()) {
            conn.prepareStatement("SHUTDOWN").execute();
        }
    }

    @Test
    void rowCountGauge() {
        DatabaseTableMetrics.monitor(registry, ds, "foo", "db.table.size");
        assertThat(registry.find("db.table.size").value(Statistic.Value, 1.0).gauge()).isPresent();
    }

    @Test
    void rowCountForNonExistentTable() {
        DatabaseTableMetrics.monitor(registry, ds, "dne", "db.table.size");
        assertThat(registry.find("db.table.size").value(Statistic.Value, 0.0).gauge()).isPresent();
    }
}
