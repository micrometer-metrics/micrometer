package io.micrometer.influx;

import java.util.*;
import java.util.stream.Stream;

/**
 * Builds a create database query for influxdb. It is supposed to be of the following structure:
 *
 * CREATE DATABASE <database_name> [WITH [DURATION <duration>] [REPLICATION <n>] [SHARD DURATION <duration>] [NAME
 * <retention-policy-name>]]
 *
 * @author Vladyslav Oleniuk (vlad.oleniuk@gmail.com)
 */
public class CreateDatabaseQueryBuilder {

	private static String QUERY_MANDATORY_TEMPLATE = "CREATE DATABASE %s";

	private static String RETENTION_POLICY_INTRODUCTION = " WITH";

	private static String DURATION_CLAUSE_TEMPLATE = " DURATION %s";

	private static String REPLICATION_FACTOR_CLAUSE_TEMPLATE = " REPLICATION %s";

	private static String SHARD_DURATION_CLAUSE_TEMPLATE = " SHARD DURATION %s";

	private static String NAME_CLAUSE_TEMPLATE = " NAME %s";

	private String databaseName;

	private String[] retentionPolicyClauses = new String[4];

	public CreateDatabaseQueryBuilder(String databaseName) {
		if (isEmpty(databaseName)) {
			throw new IllegalArgumentException("The database name mustn't be empty");
		}
		this.databaseName = databaseName;
	}

	public CreateDatabaseQueryBuilder setRetentionDuration(String retentionDuration) {
		if (!isEmpty(retentionDuration)) {
			retentionPolicyClauses[0] = String.format(DURATION_CLAUSE_TEMPLATE, retentionDuration);
		}
		return this;
	}

	public CreateDatabaseQueryBuilder setRetentionReplicationFactor(String retentionReplicationFactor) {
		if (!isEmpty(retentionReplicationFactor)) {
			retentionPolicyClauses[1] = String.format(REPLICATION_FACTOR_CLAUSE_TEMPLATE, retentionReplicationFactor);
		}
		return this;
	}

	public CreateDatabaseQueryBuilder setRetentionShardDuration(String retentionShardDuration) {
		if (!isEmpty(retentionShardDuration)) {
			retentionPolicyClauses[2] = String.format(SHARD_DURATION_CLAUSE_TEMPLATE, retentionShardDuration);
		}
		return this;
	}

	public CreateDatabaseQueryBuilder setRetentionPolicyName(String retentionPolicyName) {
		if (!isEmpty(retentionPolicyName)) {
			retentionPolicyClauses[3] = String.format(NAME_CLAUSE_TEMPLATE, retentionPolicyName);
		}
		return this;
	}

	public String build() {
		StringBuilder queryStringBuilder = new StringBuilder(String.format(QUERY_MANDATORY_TEMPLATE, databaseName));
		if (toCreateRetentionPolicy()) {
			String retentionPolicyClause = Stream.of(retentionPolicyClauses).filter(Objects::nonNull)
					.reduce(RETENTION_POLICY_INTRODUCTION, String::concat);
			queryStringBuilder.append(retentionPolicyClause);
		}
		return queryStringBuilder.toString();
	}

	private boolean toCreateRetentionPolicy() {
		return Stream.of(retentionPolicyClauses).anyMatch(Objects::nonNull);
	}

	private boolean isEmpty(String string) {
		return string == null || string.isEmpty();
	}

}
