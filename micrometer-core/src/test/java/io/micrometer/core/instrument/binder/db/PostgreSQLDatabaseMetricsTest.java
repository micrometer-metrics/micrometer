/*
 * Copyright 2019 VMware, Inc.
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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.BDDMockito;

import javax.sql.DataSource;
import java.sql.SQLException;

import static io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics.Names.*;
import static io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics.Version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Kristof Depypere
 * @author Markus Dobel
 * @author Hari Mani
 */
class PostgreSQLDatabaseMetricsTest {

    private static final String DATABASE_NAME = "test";

    private static final String FUNCTIONAL_COUNTER_KEY = "key";

    private final DataSource dataSource = mock(DataSource.class, BDDMockito.RETURNS_DEEP_STUBS);

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private final Tags tags = Tags.of("database", DATABASE_NAME);

    @Test
    void shouldRegisterPostgreSqlMetrics() throws SQLException {
        // noinspection resource
        given(dataSource.getConnection().createStatement().executeQuery("SHOW server_version").getString(1))
            .willReturn("0.0.0");
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        get(SIZE).gauge();
        get(CONNECTIONS).gauge();
        get(ROWS_FETCHED).functionCounter();
        get(ROWS_INSERTED).functionCounter();
        get(ROWS_UPDATED).functionCounter();
        get(ROWS_DELETED).functionCounter();
        get(ROWS_DEAD).gauge();

        get(BLOCKS_HITS).functionCounter();
        get(BLOCKS_READS).functionCounter();

        get(TEMP_WRITES).functionCounter();
        get(LOCKS).gauge();

        get(TRANSACTIONS).functionCounter();
        get(CHECKPOINTS_TIMED).functionCounter();
        get(CHECKPOINTS_REQUESTED).functionCounter();

        get(BUFFERS_CHECKPOINT).functionCounter();
        get(BUFFERS_CLEAN).functionCounter();
        get(BUFFERS_BACKEND).functionCounter();
    }

    @Test
    void shouldBridgePgStatReset() throws SQLException {
        // noinspection resource
        given(dataSource.getConnection().createStatement().executeQuery("SHOW server_version").getString(1))
            .willReturn("0.0.0");
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 10);

        // first reset
        Double result = metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);

        // then
        assertThat(result).isEqualTo(15);
    }

    @Test
    void shouldBridgeDoublePgStatReset() throws SQLException {
        // noinspection resource
        given(dataSource.getConnection().createStatement().executeQuery("SHOW server_version").getString(1))
            .willReturn("0.0.0");
        PostgreSQLDatabaseMetrics metrics = new PostgreSQLDatabaseMetrics(dataSource, DATABASE_NAME);
        metrics.bindTo(registry);

        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 5);
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 10);

        // first reset
        metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 3);

        // second reset
        Double result = metrics.resettableFunctionalCounter(FUNCTIONAL_COUNTER_KEY, () -> 1);

        assertThat(result).isEqualTo(14);
    }

    private RequiredSearch get(final String name) {
        return registry.get(name).tags(tags);
    }

    @Nested
    class VersionTest {

        @ParameterizedTest
        @CsvSource({ "17.6 (Debian 17.6-2.pgdg13+1), 17, 6", "9.6.24, 9, 6", "17 (Debian 17.pgdg13+1), 17, 0" })
        void parse_shouldParseGivenVersionString(final String versionString, final int expectedMajorVersion,
                final int expectedMinorVersion) {
            final Version expectedVersion = new Version(expectedMajorVersion, expectedMinorVersion);
            assertThat(Version.parse(versionString)).isEqualTo(expectedVersion);
        }

        @Test
        void parse_whenParsingFailsShouldReturnEmptyVersion() {
            final Version version = Version.parse("does not match the pattern");
            assertThat(version).isEqualTo(Version.EMPTY);
        }

        @ParameterizedTest
        @CsvSource({ "17, 0", "17, 1", "18, 0" })
        void isAtLeast_shouldReturnTrueWhenVersionIsGreaterThanOrEqual(final int majorVersion, final int minorVersion) {
            final Version version = new Version(majorVersion, minorVersion);
            assertThat(version.isAtLeast(Version.V17)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({ "16, 9", "9, 2" })
        void isAtLeast_shouldReturnFalseWhenVersionIsLesser(final int majorVersion, final int minorVersion) {
            final Version version = new Version(majorVersion, minorVersion);
            assertThat(version.isAtLeast(Version.V17)).isFalse();
        }

    }

}
