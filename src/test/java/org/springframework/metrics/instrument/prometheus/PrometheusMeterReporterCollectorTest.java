/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterReporter;
import org.springframework.metrics.instrument.MeterSamples;
import org.springframework.metrics.instrument.Tags;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PrometheusMeterReporterCollectorTest {
  @Test
  @DisplayName("collector will convert Meters to Prometheus family samples")
  void collect() {
    PrometheusMeterReporterCollector collector = new PrometheusMeterReporterCollector(new MeterReporter() {
      @Override
      public List<MeterSamples> report() {
        List<MeterSamples> meters = new ArrayList<>();
        meters.add(new MeterSamples("counter_example", Meter.Type.COUNTER, Tags.tagList("tagKey", "tagValue"), 1.0));
        meters.add(new MeterSamples("gauge_example", Meter.Type.GAUGE, Tags.tagList("tagKey", "tagValue"), 1.0));
        meters.add(new MeterSamples("summary_example", Meter.Type.TIMER, Tags.tagList("tagKey", "tagValue"), 1.0));
        meters.add(new MeterSamples("summary_long_timer_example", Meter.Type.LONG_TASK_TIMER, Tags.tagList("tagKey", "tagValue"), 1.0));
        return meters;
      }
    });

    List<Collector.MetricFamilySamples> results = collector.collect();
    assertThat(results).hasSize(4);
  }



}
