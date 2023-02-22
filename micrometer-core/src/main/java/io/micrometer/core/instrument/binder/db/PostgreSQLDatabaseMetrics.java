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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * {@link MeterBinder} for a PostgreSQL database.
 *
 * @author Kristof Depypere
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Markus Dobel
 * @since 1.1.0
 */
@NonNullApi
@NonNullFields
public class PostgreSQLDatabaseMetrics implements MeterBinder {

    private static final String SELECT = "SELECT ";

    private static final String QUERY_DEAD_TUPLE_COUNT = getUserTableQuery("SUM(n_dead_tup)");

    private static final String QUERY_TIMED_CHECKPOINTS_COUNT = getBgWriterQuery("checkpoints_timed");

    private static final String QUERY_REQUESTED_CHECKPOINTS_COUNT = getBgWriterQuery("checkpoints_req");

    private static final String QUERY_BUFFERS_CLEAN = getBgWriterQuery("buffers_clean");

    private static final String QUERY_BUFFERS_BACKEND = getBgWriterQuery("buffers_backend");

    private static final String QUERY_BUFFERS_CHECKPOINT = getBgWriterQuery("buffers_checkpoint");

    private final String database;

    private final DataSource postgresDataSource;

    private final Iterable<Tag> tags;

    private final Map<String, Double> beforeResetValuesCacheMap;

    private final Map<String, Double> previousValueCacheMap;

    private final String queryConnectionCount;

    private final String queryReadCount;

    private final String queryInsertCount;

    private final String queryTempBytes;

    private final String queryUpdateCount;

    private final String queryDeleteCount;

    private final String queryBlockHits;

    private final String queryBlockReads;

    private final String queryTransactionCount;

    public PostgreSQLDatabaseMetrics(DataSource postgresDataSource, String database) {
        this(postgresDataSource, database, Tags.empty());
    }

    public PostgreSQLDatabaseMetrics(DataSource postgresDataSource, String database, Iterable<Tag> tags) {
        this.postgresDataSource = postgresDataSource;
        this.database = database;
        this.tags = Tags.of(tags).and(createDbTag(database));
        this.beforeResetValuesCacheMap = new ConcurrentHashMap<>();
        this.previousValueCacheMap = new ConcurrentHashMap<>();

        this.queryConnectionCount = getDBStatQuery(database, "numbackends");
        this.queryReadCount = getDBStatQuery(database, "tup_fetched");
        this.queryInsertCount = getDBStatQuery(database, "tup_inserted");
        this.queryTempBytes = getDBStatQuery(database, "temp_bytes");
        this.queryUpdateCount = getDBStatQuery(database, "tup_updated");
        this.queryDeleteCount = getDBStatQuery(database, "tup_deleted");
        this.queryBlockHits = getDBStatQuery(database, "blks_hit");
        this.queryBlockReads = getDBStatQuery(database, "blks_read");
        this.queryTransactionCount = getDBStatQuery(database, "xact_commit + xact_rollback");
    }

    private static Tag createDbTag(String database) {
        return Tag.of("database", database);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(Names.SIZE, postgresDataSource, dataSource -> getDatabaseSize())
            .tags(tags)
            .description("The database size")
            .register(registry);
        Gauge.builder(Names.CONNECTIONS, postgresDataSource, dataSource -> getConnectionCount())
            .tags(tags)
            .description("Number of active connections to the given db")
            .register(registry);

        // Hit ratio can be derived from dividing hits/reads
        FunctionCounter
            .builder(Names.BLOCKS_HITS, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.BLOCKS_HITS, this::getBlockHits))
            .tags(tags)
            .description(
                    "Number of times disk blocks were found already in the buffer cache, so that a read was not necessary")
            .register(registry);
        FunctionCounter
            .builder(Names.BLOCKS_READS, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.BLOCKS_READS, this::getBlockReads))
            .tags(tags)
            .description("Number of disk blocks read in this database")
            .register(registry);

        FunctionCounter
            .builder(Names.TRANSACTIONS, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.TRANSACTIONS, this::getTransactionCount))
            .tags(tags)
            .description("Total number of transactions executed (commits + rollbacks)")
            .register(registry);
        Gauge.builder(Names.LOCKS, postgresDataSource, dataSource -> getLockCount())
            .tags(tags)
            .description("Number of locks on the given db")
            .register(registry);
        FunctionCounter
            .builder(Names.TEMP_WRITES, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.TEMP_WRITES, this::getTempBytes))
            .tags(tags)
            .description("The total amount of temporary writes to disk to execute queries")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        registerRowCountMetrics(registry);
        registerCheckpointMetrics(registry);
    }

    private void registerRowCountMetrics(MeterRegistry registry) {
        FunctionCounter
            .builder(Names.ROWS_FETCHED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.ROWS_FETCHED, this::getReadCount))
            .tags(tags)
            .description("Number of rows fetched from the db")
            .register(registry);
        FunctionCounter
            .builder(Names.ROWS_INSERTED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.ROWS_INSERTED, this::getInsertCount))
            .tags(tags)
            .description("Number of rows inserted from the db")
            .register(registry);
        FunctionCounter
            .builder(Names.ROWS_UPDATED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.ROWS_UPDATED, this::getUpdateCount))
            .tags(tags)
            .description("Number of rows updated from the db")
            .register(registry);
        FunctionCounter
            .builder(Names.ROWS_DELETED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.ROWS_DELETED, this::getDeleteCount))
            .tags(tags)
            .description("Number of rows deleted from the db")
            .register(registry);
        Gauge.builder(Names.ROWS_DEAD, postgresDataSource, dataSource -> getDeadTupleCount())
            .tags(tags)
            .description("Total number of dead rows in the current database")
            .register(registry);
    }

    private void registerCheckpointMetrics(MeterRegistry registry) {
        FunctionCounter
            .builder(Names.CHECKPOINTS_TIMED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.CHECKPOINTS_TIMED, this::getTimedCheckpointsCount))
            .tags(tags)
            .description("Number of checkpoints timed")
            .register(registry);
        FunctionCounter
            .builder(Names.CHECKPOINTS_REQUESTED, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.CHECKPOINTS_REQUESTED,
                            this::getRequestedCheckpointsCount))
            .tags(tags)
            .description("Number of checkpoints requested")
            .register(registry);

        FunctionCounter
            .builder(Names.BUFFERS_CHECKPOINT, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.BUFFERS_CHECKPOINT, this::getBuffersCheckpoint))
            .tags(tags)
            .description("Number of buffers written during checkpoints")
            .register(registry);
        FunctionCounter
            .builder(Names.BUFFERS_CLEAN, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.BUFFERS_CLEAN, this::getBuffersClean))
            .tags(tags)
            .description("Number of buffers written by the background writer")
            .register(registry);
        FunctionCounter
            .builder(Names.BUFFERS_BACKEND, postgresDataSource,
                    dataSource -> resettableFunctionalCounter(Names.BUFFERS_BACKEND, this::getBuffersBackend))
            .tags(tags)
            .description("Number of buffers written directly by a backend")
            .register(registry);
    }

    private Long getDatabaseSize() {
        return runQuery("SELECT pg_database_size('" + database + "')");
    }

    private Long getLockCount() {
        return runQuery("SELECT count(*) FROM pg_locks l JOIN pg_database d ON l.DATABASE=d.oid WHERE d.datname='"
                + database + "'");
    }

    private Long getConnectionCount() {
        return runQuery(this.queryConnectionCount);
    }

    private Long getReadCount() {
        return runQuery(this.queryReadCount);
    }

    private Long getInsertCount() {
        return runQuery(this.queryInsertCount);
    }

    private Long getTempBytes() {
        return runQuery(this.queryTempBytes);
    }

    private Long getUpdateCount() {
        return runQuery(this.queryUpdateCount);
    }

    private Long getDeleteCount() {
        return runQuery(this.queryDeleteCount);
    }

    private Long getBlockHits() {
        return runQuery(this.queryBlockHits);
    }

    private Long getBlockReads() {
        return runQuery(this.queryBlockReads);
    }

    private Long getTransactionCount() {
        return runQuery(this.queryTransactionCount);
    }

    private Long getDeadTupleCount() {
        return runQuery(QUERY_DEAD_TUPLE_COUNT);
    }

    private Long getTimedCheckpointsCount() {
        return runQuery(QUERY_TIMED_CHECKPOINTS_COUNT);
    }

    private Long getRequestedCheckpointsCount() {
        return runQuery(QUERY_REQUESTED_CHECKPOINTS_COUNT);
    }

    private Long getBuffersClean() {
        return runQuery(QUERY_BUFFERS_CLEAN);
    }

    private Long getBuffersBackend() {
        return runQuery(QUERY_BUFFERS_BACKEND);
    }

    private Long getBuffersCheckpoint() {
        return runQuery(QUERY_BUFFERS_CHECKPOINT);
    }

    /**
     * Function that makes sure functional counter values survive pg_stat_reset calls.
     */
    Double resettableFunctionalCounter(String functionalCounterKey, DoubleSupplier function) {
        Double result = function.getAsDouble();
        Double previousResult = previousValueCacheMap.getOrDefault(functionalCounterKey, 0D);
        Double beforeResetValue = beforeResetValuesCacheMap.getOrDefault(functionalCounterKey, 0D);
        Double correctedValue = result + beforeResetValue;

        if (correctedValue < previousResult) {
            beforeResetValuesCacheMap.put(functionalCounterKey, previousResult);
            correctedValue = previousResult + result;
        }
        previousValueCacheMap.put(functionalCounterKey, correctedValue);
        return correctedValue;
    }

    private Long runQuery(String query) {
        try (Connection connection = postgresDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }
        catch (SQLException ignored) {
        }
        return 0L;
    }

    private static String getDBStatQuery(String database, String statName) {
        return SELECT + statName + " FROM pg_stat_database WHERE datname = '" + database + "'";
    }

    private static String getUserTableQuery(String statName) {
        return SELECT + statName + " FROM pg_stat_user_tables";
    }

    private static String getBgWriterQuery(String statName) {
        return SELECT + statName + " FROM pg_stat_bgwriter";
    }

    static final class Names {

        static final String SIZE = of("size");
        static final String CONNECTIONS = of("connections");
        static final String BLOCKS_HITS = of("blocks.hits");
        static final String BLOCKS_READS = of("blocks.reads");
        static final String TRANSACTIONS = of("transactions");
        static final String LOCKS = of("locks");
        static final String TEMP_WRITES = of("temp.writes");

        static final String ROWS_FETCHED = of("rows.fetched");
        static final String ROWS_INSERTED = of("rows.inserted");
        static final String ROWS_UPDATED = of("rows.updated");
        static final String ROWS_DELETED = of("rows.deleted");
        static final String ROWS_DEAD = of("rows.dead");

        static final String CHECKPOINTS_TIMED = of("checkpoints.timed");
        static final String CHECKPOINTS_REQUESTED = of("checkpoints.requested");

        static final String BUFFERS_CHECKPOINT = of("buffers.checkpoint");
        static final String BUFFERS_CLEAN = of("buffers.clean");
        static final String BUFFERS_BACKEND = of("buffers.backend");

        private static String of(String name) {
            return "postgres." + name;
        }

        private Names() {
        }

    }

}
