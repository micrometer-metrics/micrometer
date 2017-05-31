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
