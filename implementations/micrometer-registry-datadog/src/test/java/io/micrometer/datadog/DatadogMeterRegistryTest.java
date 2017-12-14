/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.datadog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogMeterRegistryTest {
    private DatadogMeterRegistry sut;
    private ObjectMapper mapper;

    @BeforeEach
    void setup(){
        sut = new DatadogMeterRegistry(new DatadogMeterRegistryCompatibilityTest.FakeDatadogConfig());
        mapper = new ObjectMapper();
    }

    @Test
    void writeMetric() throws Exception {

        assertThat(datadogTypeFor(Statistic.Count)).isEqualTo("count");
        assertThat(datadogTypeFor(Statistic.Total)).isEqualTo("count");
        assertThat(datadogTypeFor(Statistic.TotalTime)).isEqualTo("count");

        assertThat(datadogTypeFor(Statistic.Max)).isEqualTo("gauge");
        assertThat(datadogTypeFor(Statistic.Value)).isEqualTo("gauge");
        assertThat(datadogTypeFor(Statistic.Unknown)).isEqualTo("gauge");
        assertThat(datadogTypeFor(Statistic.ActiveTasks)).isEqualTo("gauge");
        assertThat(datadogTypeFor(Statistic.Duration)).isEqualTo("gauge");
    }

    @Test
    void unitIsPresent() throws IOException {
        String metric = sut.writeMetric(new Meter.Id("meter.without.unit", Tags.zip(), "", "", Meter.Type.Counter), Statistic.Count, 123, 1.23);
        assertThat(metric).doesNotContain("\"unit\"");
    }

    @Test
    void unitIsOmittedWhenNotPresent() throws IOException {
        assertThat(datadogMetric(Statistic.Duration).get("unit")).isEqualTo("testUnit");
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, Object> datadogMetric(Statistic statistic) throws IOException {
        String json = sut.writeMetric(createId(statistic), statistic, 123, 1.23);
        return mapper.readValue(json, HashMap.class);
    }

    @SuppressWarnings("unchecked")
    private String datadogTypeFor(Statistic statistic) throws IOException {
        return (String) datadogMetric(statistic).get("type");
    }

    private Meter.Id createId(Statistic type) {
        return new Meter.Id("test."+type.name().toLowerCase(), Tags.zip("tag1","val1"), "testUnit", "Desc", Meter.Type.Other);
    }
}
