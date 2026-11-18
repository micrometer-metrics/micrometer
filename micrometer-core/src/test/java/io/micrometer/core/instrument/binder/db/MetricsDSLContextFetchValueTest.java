package io.micrometer.core.instrument.binder.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;

import org.jooq.Configuration;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;

/**
 * Reproduces bug #6659: when using jOOQ 3.20+ overload
 * DSLContext#fetchValue(SelectField<T>) the MetricsDSLContext can instrument twice,
 * recording two timer samples and losing tags.
 */
class MetricsDSLContextFetchValueTest {

    @Test
    void fetchValueShouldRecordSingleTimer() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")) {
            conn.createStatement().execute("CREATE TABLE test_table (id INT)");

            Configuration configuration = new DefaultConfiguration()
                    .set(conn)
                    .set(SQLDialect.H2);

            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MetricsDSLContext jooq = MetricsDSLContext.withMetrics(DSL.using(configuration), registry, Tags.empty());

            // This path used to be double-instrumented before the fix
            Integer result = jooq.tag("name", "fetchValue").fetchValue(DSL.inline(42));

            assertThat(result).isEqualTo(42);
            Timer timer = registry.get("jooq.query").tag("name", "fetchValue").timer();
            assertThat(timer.count()).isEqualTo(1);
        }
    }
}
