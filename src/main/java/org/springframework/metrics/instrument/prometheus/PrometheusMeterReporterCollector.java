package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterReporter;
import org.springframework.metrics.instrument.MeterSamples;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.MeterId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PrometheusMeterReporterCollector extends Collector {
  private final MeterReporter meterReporter;

  public PrometheusMeterReporterCollector(MeterReporter meterReporter) {
    this.meterReporter = meterReporter;
  }

  @Override
  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> metrics = new ArrayList<>();
    for(MeterSamples sample: meterReporter.report()) {
      Type type = convertType(sample.getType());
      MetricFamilySamples familySamples = new MetricFamilySamples(sample.getName(), type," ",
              convertSamples(sample.getSamples()));
      metrics.add(familySamples);
    }
    return metrics;
  }

  private List<MetricFamilySamples.Sample> convertSamples(List<MeterSamples.Sample> samples) {
    return samples.stream().map(sample -> {
      MeterId id = sample.getId();
      List<String> tagKeys = Arrays.stream(id.getTags()).map(Tag::getKey).collect(Collectors.toList());
      List<String> tagValues = Arrays.stream(id.getTags()).map(Tag::getValue).collect(Collectors.toList());

      return new MetricFamilySamples.Sample(id.getName(), tagKeys, tagValues, sample.getValue());
    }).collect(Collectors.toList());
  }

  private Type convertType(Meter.Type type) {
    Type result = null;
    switch (type) {
      case COUNTER:
        result = Type.COUNTER;
        break;
      case GAUGE:
        result = Type.GAUGE;
        break;
      case DISTRIBUTION_SUMMARY:
        result = Type.SUMMARY;
        break;
      case LONG_TASK_TIMER:
        result = Type.SUMMARY;
        break;
      case TIMER:
        result = Type.SUMMARY;
        break;
    }
    return result;
  }
}
