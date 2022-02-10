/*
 * Copyright 2020 VMware, Inc.
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

package io.micrometer.prometheus;

import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.ExemplarSampler;

/**
 * TraceSamplingCompliantExemplarSampler
 * <p>
 * Keeps each Exemplar for a minimum of ~7 seconds, then samples a new one if
 * Traces we have to associate with the metrics is also sampled
 */
public class TraceSamplingCompliantExemplarSampler implements ExemplarSampler {

  private static final String SPAN_ID = "span_id";
  private static final String TRACE_ID = "trace_id";

  private final OtelMicroMeterSpanContextSupplier spanContextSupplier;
  // Choosing a prime number for the retention interval makes behavior more predictable,
  // because it is unlikely that retention happens at the exact same time as a Prometheus scrape.
  private final long minRetentionIntervalMs = 7109;
  private final Clock clock;

  public TraceSamplingCompliantExemplarSampler(OtelMicroMeterSpanContextSupplier spanContextSupplier) {
    this.spanContextSupplier = spanContextSupplier;
    this.clock = new SystemClock();
  }

  // for unit tests only
  TraceSamplingCompliantExemplarSampler(OtelMicroMeterSpanContextSupplier spanContextSupplier, Clock clock) {
    this.spanContextSupplier = spanContextSupplier;
    this.clock = clock;
  }

  @Override
  public Exemplar sample(double increment, Exemplar previous) {
    return doSample(increment, previous);
  }

  @Override
  public Exemplar sample(double value, double bucketFrom, double bucketTo, Exemplar previous) {
    return doSample(value, previous);
  }

  private Exemplar doSample(double value, Exemplar previous) {
    long timestampMs = clock.currentTimeMillis();
    if ((previous == null || previous.getTimestampMs() == null || timestampMs - previous.getTimestampMs() > minRetentionIntervalMs) && spanContextSupplier.isSampled()) {
      String spanId = spanContextSupplier.getSpanId();
      String traceId = spanContextSupplier.getTraceId();
      if (traceId != null && spanId != null) {
        return new Exemplar(value, timestampMs, SPAN_ID, spanId, TRACE_ID, traceId);
      }
    }
    return null;
  }

  interface Clock {
    long currentTimeMillis();
  }

  static class SystemClock implements Clock {
    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
