package io.micrometer.spring.jdbc.db;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * @author Kristof Depypere
 */
@NonNullApi
@NonNullFields
public class PostgreSQLDatabaseMetrics implements MeterBinder {

    private final Logger logger = LoggerFactory.getLogger(PostgreSQLDatabaseMetrics.class);

    private final String database;
    private final DataSource dataSource;
    private final Iterable<Tag> tags;

    public PostgreSQLDatabaseMetrics(DataSource dataSource, String database) {
        this(dataSource, database, Tags.of(createDbTag(database)));
    }

    private static Tag createDbTag(String database) {
        return Tag.of("database", database);
    }

    public PostgreSQLDatabaseMetrics(DataSource dataSource, String database, Iterable<Tag> tags) {
        this.dataSource = dataSource;
        this.database = database;
        this.tags = Tags.of(tags).and(createDbTag(database));
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("postgres.size", dataSource, dataSource -> getDatabaseSize())
            .tags(tags)
            .description("The database size")
            .register(registry);
        Gauge.builder("postgres.connections", dataSource, dataSource -> getConnectionCount())
            .tags(tags)
            .description("Number of active connections to the given db")
            .register(registry);
        Gauge.builder("postgres.rows.fetched", dataSource, dataSource -> getReadCount())
            .tags(tags)
            .description("Number of rows fetched from the db")
            .register(registry);
        Gauge.builder("postgres.rows.inserted", dataSource, dataSource -> getInsertCount())
            .tags(tags)
            .description("Number of rows inserted from the db")
            .register(registry);
        Gauge.builder("postgres.rows.updated", dataSource, dataSource -> getUpdateCount())
            .tags(tags)
            .description("Number of rows updated from the db")
            .register(registry);
        Gauge.builder("postgres.rows.deleted", dataSource, dataSource -> getDeleteCount())
            .tags(tags)
            .description("Number of rows deleted from the db")
            .register(registry);
        Gauge.builder("postgres.hitratio", dataSource, dataSource -> getHitRatio())
            .tags(tags)
            .description("Percentage of blocks in this database that were shared buffer hits vs. read from disk")
            .register(registry);
        Gauge.builder("postgres.transactions", dataSource, dataSource -> getTransactionCount())
            .tags(tags)
            .description("Total number of transactions executed (commits + rollbacks)")
            .register(registry);

        Gauge.builder("postgres.rows.dead", dataSource, dataSource -> getDeadTupleCount())
            .tags(tags)
            .description("Total number of dead rows in the current database")
            .register(registry);
        Gauge.builder("postgres.checkpoints.timed", dataSource, dataSource -> getTimedCheckpointsCount())
            .tags(tags)
            .description("Number of checkpoints timed")
            .register(registry);
        Gauge.builder("postgres.checkpoints.req", dataSource, dataSource -> getRequestedCheckpointsCount())
            .tags(tags)
            .description("Number of checkpoints requested")
            .register(registry);
        Gauge.builder("postgres.checkpoints.bufferratio", dataSource, dataSource -> getCheckpointBufferRatio())
            .tags(tags)
            .description("The percentage of total buffer written during a checkpoint vs total buffers written")
            .register(registry);
    }

    protected Long getDatabaseSize() {
        return runQuery("SELECT pg_database_size('" + database + "')", Long.class);
    }

    protected int getConnectionCount() {
        String query = getDBStatQuery("SUM(numbackends)");
        return runQuery(query, Integer.class);
    }

    protected long getReadCount() {
        String query = getDBStatQuery("tup_fetched");
        return runQuery(query, Integer.class);
    }

    protected long getInsertCount() {
        String query = getDBStatQuery("tup_inserted");
        return runQuery(query, Integer.class);
    }

    protected long getUpdateCount() {
        String query = getDBStatQuery("tup_updated");
        return runQuery(query, Integer.class);
    }

    protected long getDeleteCount() {
        String query = getDBStatQuery("tup_deleted");
        return runQuery(query, Integer.class);
    }

    protected Float getHitRatio() {
        String query = getDBStatQuery("(blks_hit*100)::NUMERIC / (blks_hit+blks_read)");
        return runQuery(query, Float.class);
    }

    protected Long getTransactionCount() {
        String query = getDBStatQuery("xact_commit + xact_rollback");
        return runQuery(query, Long.class);
    }

    protected Integer getDeadTupleCount() {
        String query = getUserTableQuery("n_dead_tup");
        return runQuery(query, Integer.class);
    }

    protected Integer getTimedCheckpointsCount() {
        String query = getBgWriterQuery("checkpoints_timed");
        return runQuery(query, Integer.class);
    }
    protected Integer getRequestedCheckpointsCount() {
        String query = getBgWriterQuery("checkpoints_req");
        return runQuery(query, Integer.class);
    }

    protected Float getCheckpointBufferRatio() {
        String query = getBgWriterQuery("(buffers_checkpoint*100)::NUMERIC / (buffers_backend+buffers_clean+buffers_checkpoint)");
        return runQuery(query, Float.class);
    }

    private <T> T runQuery(String query, Class<T> returnClass) {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            return resultSet.getObject(1, returnClass);
        } catch (SQLException e) {
            logger.error("Error getting statistic from postgreSQL database");
            return null;
        }
    }

    private String getDBStatQuery(String statName) {
        return "SELECT " + statName + " FROM pg_stat_database WHERE datname = '"+database+"'";
    }

    private String getUserTableQuery(String statName) {
        return "SELECT " + statName + " FROM pg_stat_user_tables";
    }

    private String getBgWriterQuery(String statName) {
        return "SELECT " + statName + " FROM pg_stat_bgwriter";
    }
}
