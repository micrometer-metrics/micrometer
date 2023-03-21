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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class DatabaseTableMetrics implements MeterBinder {

    private final DataSource dataSource;

    private final String query;

    private final String dataSourceName;

    private final String tableName;

    private final Iterable<Tag> tags;

    /**
     * Record the row count for an individual database table.
     * @param dataSource The data source to use to run the row count query.
     * @param dataSourceName Will be used to tag metrics with "db".
     * @param tableName The name of the table to report table size for.
     * @param tags Tags to apply to all recorded metrics.
     */
    public DatabaseTableMetrics(DataSource dataSource, String dataSourceName, String tableName, Iterable<Tag> tags) {
        this(dataSource, "SELECT COUNT(1) FROM " + tableName, dataSourceName, tableName, tags);
    }

    /**
     * Record the result based on a query.
     * @param dataSource The data source to use to run the row count query.
     * @param query The query to be run against the table. The first column of the result
     * will be the metric and it should return a single row.
     * @param dataSourceName The name prefix of the metrics.
     * @param tableName The name of the table to report table size for.
     * @param tags Tags to apply to all recorded metrics.
     */
    public DatabaseTableMetrics(DataSource dataSource, String query, String dataSourceName, String tableName,
            Iterable<Tag> tags) {
        this.dataSource = dataSource;
        this.query = query;
        this.dataSourceName = dataSourceName;
        this.tableName = tableName;
        this.tags = tags;
    }

    /**
     * Record the row count for an individual database table.
     * @param registry The registry to bind metrics to.
     * @param tableName The name of the table to report table size for.
     * @param dataSourceName Will be used to tag metrics with "db".
     * @param dataSource The data source to use to run the row count query.
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     */
    public static void monitor(MeterRegistry registry, String tableName, String dataSourceName, DataSource dataSource,
            String... tags) {
        monitor(registry, dataSource, dataSourceName, tableName, Tags.of(tags));
    }

    /**
     * Record the row count for an individual database table.
     * @param registry The registry to bind metrics to.
     * @param dataSource The data source to use to run the row count query.
     * @param dataSourceName The name prefix of the metrics.
     * @param tableName The name of the table to report table size for.
     * @param tags Tags to apply to all recorded metrics.
     */
    public static void monitor(MeterRegistry registry, DataSource dataSource, String dataSourceName, String tableName,
            Iterable<Tag> tags) {
        new DatabaseTableMetrics(dataSource, dataSourceName, tableName, tags).bindTo(registry);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ToDoubleFunction<DataSource> totalRows = ds -> {
            try (Connection conn = ds.getConnection();
                    PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
            catch (SQLException ignored) {
                return 0;
            }
        };

        Gauge.builder("db.table.size", dataSource, totalRows)
            .tags(tags)
            .tag("db", dataSourceName)
            .tag("table", tableName)
            .description("Number of rows in a database table")
            .baseUnit(BaseUnits.ROWS)
            .register(registry);
    }

}
