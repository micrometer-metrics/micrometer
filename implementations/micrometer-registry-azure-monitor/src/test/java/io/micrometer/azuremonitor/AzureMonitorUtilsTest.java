/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.azuremonitor;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.micrometer.azuremonitor.AzureMonitorUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AzureMonitorUtilsTest {

    @Test
    void testExtractInstrumentationKeyFromConnectionString() {
        final String expectedInstrumentationKey = UUID.randomUUID().toString();

        final String connectionString = String.format(
                "InstrumentationKey=%s;IngestionEndpoint=https://westeurope-5.in.applicationinsights.azure.com/;LiveEndpoint=https://westeurope.livediagnostics.monitor.azure.com/",
                expectedInstrumentationKey);

        final String actualInstrumentationKey = extractInstrumentationKeyFromConnectionString(connectionString);

        assertThat(actualInstrumentationKey).isEqualTo(expectedInstrumentationKey);
    }

    @Test
    void testExtractInstrumentationKeyFromConnectionString_missing() {
        final String expectedInstrumentationKey = "";

        final String connectionString = "IngestionEndpoint=https://westeurope-5.in.applicationinsights.azure.com/;LiveEndpoint=https://westeurope.livediagnostics.monitor.azure.com/";

        final String actualInstrumentationKey = extractInstrumentationKeyFromConnectionString(connectionString);

        assertThat(actualInstrumentationKey).isEqualTo(expectedInstrumentationKey);
    }

    @Test
    void testExtractInstrumentationKeyFromConnectionString_empty() {
        final String expectedInstrumentationKey = "";

        final String connectionString = "";

        final String actualInstrumentationKey = extractInstrumentationKeyFromConnectionString(connectionString);

        assertThat(actualInstrumentationKey).isEqualTo(expectedInstrumentationKey);
    }

    @Test
    void testExtractInstrumentationKeyFromConnectionString_null() {
        final String connectionString = null;

        assertThrows(NullPointerException.class, () -> extractInstrumentationKeyFromConnectionString(connectionString));
    }

}
