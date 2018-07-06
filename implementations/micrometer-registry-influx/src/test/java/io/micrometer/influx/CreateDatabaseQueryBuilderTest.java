package io.micrometer.influx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link CreateDatabaseQueryBuilder}
 */
public class CreateDatabaseQueryBuilderTest {

	/**
	 * Class Parameters:
	 *
	 * - database name
	 * - retention clauses
	 *
	 * Class Criteria
	 * - database name is not null		->	true		false
	 * - retention clauses			->	no		one		all
	 */

	private CreateDatabaseQueryBuilder createDatabaseQueryBuilder;
	private static String TEST_DB_NAME = "dummy_database_0";

	@BeforeEach
	public void init() {
		createDatabaseQueryBuilder = new CreateDatabaseQueryBuilder(TEST_DB_NAME);
	}

	@Test
	public void noEmptyDatabaseName() {
		assertThrows(IllegalArgumentException.class, () -> new CreateDatabaseQueryBuilder(null));
	}

	@Test
	public void noRetentionPolicy() {
		String query = createDatabaseQueryBuilder.build();
		assertEquals("CREATE DATABASE dummy_database_0", query);
	}

	@Test
	public void oneClauseInRetentionPolicy() {
		String query = createDatabaseQueryBuilder.setRetentionPolicyName("dummy_policy").build();
		assertEquals("CREATE DATABASE dummy_database_0 WITH NAME dummy_policy", query);
	}

	@Test
	public void allClausesInRetentionPolicy() {
		String query = createDatabaseQueryBuilder.setRetentionPolicyName("dummy_policy")
				.setRetentionDuration("2d")
				.setRetentionReplication("1")
				.setRetentionShardDuration("3")
				.build();
		assertEquals("CREATE DATABASE dummy_database_0 WITH DURATION 2d REPLICATION 1 SHARD DURATION 3 NAME dummy_policy", query);
	}

}
