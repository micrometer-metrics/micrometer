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

    private final String query;
    private final String tableName;
    private final String name;
    private final Iterable<Tag> tags;
    private final DataSource dataSource;

    /**
     * Record the row count for an individual database table.
     *
     * @param dataSource The data source to use to run the row count query.
     * @param tableName  The name of the table to report table size for.
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     */
    public DatabaseTableMetrics(DataSource dataSource, String tableName, String name, Iterable<Tag> tags) {
        this(dataSource, "SELECT COUNT(1) FROM " + tableName, tableName, name, tags);
    }

    /**
     * Record the result based on a query.
     *
     * @param dataSource The data source to use to run the row count query.
     * @param query      The query to be run against the table. The first column of the result will be the metric and
     *                   it should return a single row.
     * @param tableName  The name of the table to report table size for.
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     */
    public DatabaseTableMetrics(DataSource dataSource, String query, String tableName, String name, Iterable<Tag> tags) {
        this.dataSource = dataSource;
        this.query = query;
        this.tableName = tableName;
        this.name = name;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(name, Tags.concat(tags,"table", tableName), dataSource, ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            } catch(SQLException ignored) {
                return 0;
            }
        });
    }
}
