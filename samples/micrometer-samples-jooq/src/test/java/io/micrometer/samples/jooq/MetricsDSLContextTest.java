/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.samples.jooq;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.db.MetricsDSLContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static io.micrometer.core.instrument.binder.db.MetricsDSLContext.withMetrics;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.jooq.impl.DSL.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MetricsDSLContext} run on the latest version of jOOQ.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class MetricsDSLContextTest {

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void timeFluentSelectStatement() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            Result<Record> result = jooq.tag("name", "selectAllAuthors").select(asterisk()).from("author").fetch();
            assertThat(result.size()).isEqualTo(1);

            // intentionally don't time this operation to demonstrate that the
            // configuration hasn't been globally mutated
            jooq.select(asterisk()).from("author").fetch();

            assertThat(
                    meterRegistry.get("jooq.query").tag("name", "selectAllAuthors").tag("type", "read").timer().count())
                .isEqualTo(1);
        }
    }

    @Test
    void timeParsedSelectStatement() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:parsedSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);
            jooq.tag("name", "selectAllAuthors").fetch("SELECT * FROM author");

            // intentionally don't time this operation to demonstrate that the
            // configuration hasn't been globally mutated
            jooq.fetch("SELECT * FROM author");

            assertThat(
                    meterRegistry.get("jooq.query").tag("name", "selectAllAuthors").tag("type", "read").timer().count())
                .isEqualTo(1);
        }
    }

    @Test
    void timeFaultySelectStatement() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:faultySelect")) {
            MetricsDSLContext jooq = createDatabase(conn);
            jooq.tag("name", "selectAllAuthors").fetch("SELECT non_existent_field FROM author");

            failBecauseExceptionWasNotThrown(DataAccessException.class);
        }
        catch (DataAccessException ignored) {
        }

        assertThat(meterRegistry.get("jooq.query")
            .tag("name", "selectAllAuthors")
            .tag("type", "read")
            .tag("exception", "c42 syntax error or access rule violation")
            .tag("exception.subclass", "none")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeExecute() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            jooq.tag("name", "selectAllAuthors").execute("SELECT * FROM author");

            assertThat(meterRegistry.get("jooq.query").tag("name", "selectAllAuthors").timer().count()).isEqualTo(1);
        }
    }

    @Test
    void timeInsert() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            jooq.tag("name", "insertAuthor").insertInto(table("author")).values("2", "jon", "schneider").execute();

            assertThat(meterRegistry.get("jooq.query").tag("name", "insertAuthor").timer().count()).isEqualTo(1);
        }
    }

    @Test
    void timeUpdate() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            jooq.tag("name", "updateAuthor").update(table("author")).set(field("author.first_name"), "jon").execute();

            assertThat(meterRegistry.get("jooq.query").tag("name", "updateAuthor").timer().count()).isEqualTo(1);
        }
    }

    @Test
    void timingBatchQueriesNotSupported() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            jooq.tag("name", "batch")
                .batch(jooq.insertInto(table("author")).values("3", "jon", "schneider"),
                        jooq.insertInto(table("author")).values("4", "jon", "schneider"))
                .execute();

            assertThat(meterRegistry.find("jooq.query").timer()).isNull();
        }
    }

    /**
     * Ensures that we are holding tag state in a way that doesn't conflict between two
     * unexecuted queries.
     */
    @Test
    void timeTwoStatementsCreatedBeforeEitherIsExecuted() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:fluentSelect")) {
            MetricsDSLContext jooq = createDatabase(conn);

            SelectJoinStep<Record> select1 = jooq.tag("name", "selectAllAuthors").select(asterisk()).from("author");
            SelectJoinStep<Record1<Object>> select2 = jooq.tag("name", "selectAllAuthors2")
                .select(field("first_name"))
                .from("author");

            select1.fetch();
            select2.fetch();
            select1.fetch();

            assertThat(meterRegistry.get("jooq.query").tag("name", "selectAllAuthors").timer().count()).isEqualTo(2);

            assertThat(meterRegistry.get("jooq.query").tag("name", "selectAllAuthors2").timer().count()).isEqualTo(1);
        }
    }

    @Test
    void userExecuteListenerShouldBePreserved() {
        ExecuteListener userExecuteListener = mock(ExecuteListener.class);
        Configuration configuration = new DefaultConfiguration().set(() -> userExecuteListener);

        MetricsDSLContext jooq = withMetrics(using(configuration), meterRegistry, Tags.empty());

        ExecuteListenerProvider[] executeListenerProviders = jooq.configuration().executeListenerProviders();
        assertThat(executeListenerProviders).hasSize(2);
        assertThat(executeListenerProviders[0].provide()).isSameAs(userExecuteListener);
        assertThat(executeListenerProviders[1].provide().getClass().getCanonicalName())
            .isEqualTo("io.micrometer.core.instrument.binder.db.JooqExecuteListener");

        SelectSelectStep<Record> select = jooq.tag("name", "selectAllAuthors").select(asterisk());
        executeListenerProviders = select.configuration().executeListenerProviders();
        assertThat(executeListenerProviders).hasSize(2);
        assertThat(executeListenerProviders[0].provide()).isSameAs(userExecuteListener);
        assertThat(executeListenerProviders[1].provide().getClass().getCanonicalName())
            .isEqualTo("io.micrometer.core.instrument.binder.db.JooqExecuteListener");
    }

    @NonNull
    private MetricsDSLContext createDatabase(Connection conn) {
        Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.H2);

        MetricsDSLContext jooq = MetricsDSLContext.withMetrics(DSL.using(configuration), meterRegistry, Tags.empty());

        jooq.execute("CREATE TABLE author (" + "  id int NOT NULL," + "  first_name varchar(255) DEFAULT NULL,"
                + "  last_name varchar(255) DEFAULT NULL," + "  PRIMARY KEY (id)" + ")");

        jooq.execute("INSERT INTO author VALUES(1, 'jon', 'schneider')");
        return jooq;
    }

}
