package io.micrometer.core.instrument.binder.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Jon Schneider
 */
public class DatabaseTableMetrics implements MeterBinder {
    /**
     * Record the row count for an individual database table.
     *
     * @param registry  The registry to bind metrics to.
     * @param ds        The data source to use to run the row count query.
     * @param tableName The name of the table to report table size for.
     * @param name      The name prefix of the metrics.
     * @param tags      Tags to apply to all recorded metrics.
     */
    public static void monitor(MeterRegistry registry, DataSource ds, String tableName, String name, String... tags) {
        monitor(registry, ds, tableName, name, Tags.zip(tags));
    }

    /**
     * Record the row count for an individual database table.
     *
     * @param registry  The registry to bind metrics to.
     * @param ds        The data source to use to run the row count query.
     * @param tableName The name of the table to report table size for.
     * @param name      The name prefix of the metrics.
     * @param tags      Tags to apply to all recorded metrics.
     */
    public static void monitor(MeterRegistry registry, DataSource ds, String tableName, String name, Iterable<Tag> tags) {
        new DatabaseTableMetrics(ds, tableName, name, tags).bindTo(registry);
    }

    private final String tableName;
    private final String name;
    private final Iterable<Tag> tags;
    private final DataSource dataSource;

    public DatabaseTableMetrics(DataSource dataSource, String tableName, String name, Iterable<Tag> tags) {
        this.tableName = tableName;
        this.name = name;
        this.tags = tags;
        this.dataSource = dataSource;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(name, Tags.concat(tags,"table", tableName), dataSource, ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(1) FROM " + tableName);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            } catch(SQLException ignored) {
                return 0;
            }
        });
    }
}
