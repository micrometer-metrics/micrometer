/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.bigquery;

import com.google.auth.Credentials;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getBoolean;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;

/**
 * Configuration for {@link BigQueryMeterRegistry}.
 *
 * @author Lui Baeumer
 */
public interface BigQueryConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    BigQueryConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "bigquery";
    }

    /**
     * @return The BigQuery dataset. Defaults to "appmetrics".
     */
    default String dataset() {
        return getString(this, "dataset").orElse("appmetrics");
    }

    /**
     * Auto-creating the dataset including table.
     *
     * @return {@code true} if Micrometer should check if {@link #dataset()} exists before attempting to publish
     * metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateDataset() {
        return getBoolean(this, "autoCreateDb").orElse(false);
    }

    /**
     * Auto-creating the missing fields in BigQuery.
     *
     * @return {@code true} if Micrometer should check if {@link #dataset()} exists before attempting to publish
     * metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateFields() {
        return getBoolean(this, "autoCreateFields").orElse(false);
    }

    /**
     * Clear the registry after sending data.
     *
     * @return {@code true} if Micrometer should clear the registry metrics after sending. This results in sending metrics only once to BigQuery.
     */
    default boolean skipZeroCounter() {
        return getBoolean(this, "skipZeroCounter").orElse(false);
    }

    /**
     * Defining a regular expression that excludes measurements from being sent to BigQuery.
     *
     * @return {@code true} a regular expression that matches measurement names or null if all measurements should be sent.
     */
    default String measurementExclusionFilter() {
        return getString(this, "measurementExclusionFilter").orElse(null);
    }

    /**
     * Specifies Google Cloud Platform project which runs your BigQuery tables.
     *
     * @return The Google Cloud Project ID.
     */
    default String projectId() {
        return getString(this, "projectId").required().get();
    }

    /**
     * Specifies the BigQuery table for writes.
     *
     * @return The destination table for writes.
     */
    default String table() {
        return getString(this, "table").orElse("metrics");
    }

    /**
     * Return {@link Credentials} to use.
     *
     * @return {@code Credentials} to use
     */
    default Credentials credentials() {
        return null;
    }

    /**
     * The proxy host.
     *
     * @return proxy hostname
     */
    default String proxyHost() {
        return null;
    }

    /**
     * The proxy port.
     *
     * @return proxy port
     */
    default Integer proxyPort() {
        return null;
    }

    /**
     * The BigQuery location.
     *
     * @return location
     */
    default String location() {
        return null;
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("projectId", BigQueryConfig::projectId),
                checkRequired("dataset", BigQueryConfig::dataset),
                checkRequired("table", BigQueryConfig::table)
        );
    }
}
