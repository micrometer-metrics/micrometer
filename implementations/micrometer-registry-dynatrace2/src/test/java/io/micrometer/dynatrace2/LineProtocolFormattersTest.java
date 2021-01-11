/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.micrometer.dynatrace2.LineProtocolFormatters.formatCounterMetricLine;
import static io.micrometer.dynatrace2.LineProtocolFormatters.formatGaugeMetricLine;
import static io.micrometer.dynatrace2.LineProtocolFormatters.formatTimerMetricLine;


class LineProtocolFormattersTest implements WithAssertions {

    DynatraceMeterRegistry meterRegistry;
    Clock clock;
    NamingConvention namingConvention = new LineProtocolNamingConvention();

    @BeforeEach
    void setUpConfigAndMeterRegistry(){
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return "API_TOKEN";
            }

            @Override
            public String uri() {
                return "https://dynatrace.com";
            }

            @Override
            public String entityId() {return ""; }
        };
        clock = new MockClock();
        meterRegistry = DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .build();
    }

    @Test
    void shouldCreateAGaugeMetricLine_whenDimensionsAreEmpty() {
        meterRegistry.gauge("my.metric", 55);

        List<String> metricLines = new ArrayList<>();

        for(Meter meter : meterRegistry.getMeters()){
            for(Measurement measurement : meter.measure()) {
                    String metricLine = formatGaugeMetricLine(namingConvention, meter, measurement, 12345);
                    metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my.metric 55 12345");
    }

    @Test
    void shouldCreateAGaugeMetricLine_whenMultipleDimensions() {
        meterRegistry.gauge("my.metric",Tags.of("country", "es", "city", "bcn"), 3.33);

        List<String> metricLines = new ArrayList<>();

        for(Meter meter : meterRegistry.getMeters()){
            for(Measurement measurement : meter.measure()) {
                String metricLine = formatGaugeMetricLine(namingConvention, meter, measurement, 12345);
                metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my.metric,city=\"bcn\",country=\"es\" 3.33 12345");
    }

    @Test
    void shouldCreateACounterMetricLine_whenDimensionsAreEmpty() {
        Counter counterMeter = meterRegistry.counter("my.metric");

        List<String> metricLines = new ArrayList<>();

        for(Measurement measurement : counterMeter.measure()){
            String metricLine = formatCounterMetricLine(namingConvention, counterMeter, measurement, 12345);
            metricLines.add(metricLine);
        }
        assertThat(metricLines).contains("my.metric count,delta=0 12345");

    }

    @Test
    void shouldCreateACounterMetricLine_whenMultipleDimensions() {
        Counter counterMeter = Counter.builder("my.metric").tag("country", "es").tag("city", "bcn").register(meterRegistry);

        List<String> metricLines = new ArrayList<>();

        for(Measurement measurement : counterMeter.measure()) {
            String metricLine = formatCounterMetricLine(namingConvention, counterMeter, measurement, 12345);
            metricLines.add(metricLine);
        }

        assertThat(metricLines).contains("my.metric,city=\"bcn\",country=\"es\" count,delta=0 12345");
    }

    @Test
    void shouldCreateATimerMetricLine_whenDimensionsAreEmpty() {
        meterRegistry.timer("my.metric");

        List<String> metricLines = new ArrayList<>();

        for(Meter meter : meterRegistry.getMeters()){
            for(Measurement measurement : meter.measure()) {
                String metricLine = formatTimerMetricLine(namingConvention, meter, measurement, 12345);
                metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my.metric.count gauge,0 12345");
    }

    @Test
    void shouldCreateATimerMetricLine_whenMultipleDimensions() {
        meterRegistry.timer("my.metric", Tags.of("country", "es", "city", "bcn"));

        List<String> metricLines = new ArrayList<>();

        for(Meter meter : meterRegistry.getMeters()){
            for(Measurement measurement : meter.measure()) {
                String metricLine = formatTimerMetricLine(namingConvention, meter, measurement, 12345);
                metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my.metric.count,city=\"bcn\",country=\"es\" gauge,0 12345");
    }


    @Test
    void shouldSucceed_whenTagKeyContainsSpecialChar() {
        meterRegistry.timer("my.metric", Tags.of("country#lang", "es", "city", "bcn"));

        List<String> metricLines = new ArrayList<>();

        for (Meter meter : meterRegistry.getMeters()) {
            for (Measurement measurement : meter.measure()) {
                String metricLine = formatTimerMetricLine(namingConvention, meter, measurement, 12345);
                metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my.metric.count,city=\"bcn\",country_lang=\"es\" gauge,0 12345");
    }

    @Test
    void shouldSucceed_whenMeticNameContainsSpecialChar() {
        meterRegistry.timer("My#Metric");

        List<String> metricLines = new ArrayList<>();

        for (Meter meter : meterRegistry.getMeters()) {
            for (Measurement measurement : meter.measure()) {
                String metricLine = formatTimerMetricLine(namingConvention, meter, measurement, 12345);
                metricLines.add(metricLine);
            }
        }

        assertThat(metricLines).contains("my_metric.count gauge,0 12345");
    }
}
