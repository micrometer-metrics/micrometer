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
package io.micrometer.elastic;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.elastic.ElasticSerializableMeters.*;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

class ElasticMetricsModule extends Module {
    // TODO: there must be a better way!
    private static final Version VERSION = new Version(1, 0, 0, null, "io.micrometer", "micrometer-registry-elastic");

    private static abstract class AbstractElasticMeterSerializer<T extends ElasticSerializableMeter<M>, M extends Meter> extends StdSerializer<T>  {
        private final DecimalFormat df = new DecimalFormat("#.####", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        private final NamingConvention namingConvention;
        final TimeUnit rateUnit;
        final TimeUnit durationUnit;
        private final String timeStampFieldName;
        private final String metricPrefix;

        AbstractElasticMeterSerializer(Class<T> t, NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(t);
            this.namingConvention = namingConvention;
            this.rateUnit = rateUnit;
            this.durationUnit = durationUnit;
            this.timeStampFieldName = timeStampFieldName;
            this.metricPrefix = metricPrefix;
        }

        @Override
        public void serialize(T meter, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartObject();

            json.writeObjectField(timeStampFieldName, new Date(meter.getTimestamp()));
            json.writeStringField("type", meter.getType());

            json.writeStringField("name", metricPrefix + meter.getMeter().getId().getConventionName(namingConvention));
            for (Tag t : meter.getMeter().getId().getTags()) {
                json.writeStringField(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue()));
            }

            serialize(json, meter.getMeter());

            json.writeEndObject();
        }

        protected abstract void serialize(JsonGenerator json, M meter) throws IOException;

        void writePercentiles(JsonGenerator json, ValueAtPercentile[] percentiles) throws IOException {
            for (ValueAtPercentile p : percentiles) {
                json.writeNumberField(writePercentile(p.percentile()), p.value(durationUnit));
            }
        }

        void writeCounts(JsonGenerator json, CountAtValue[] counts) throws IOException {
            for (CountAtValue c : counts) {
                json.writeNumberField(writeCount(c.count()), c.value(rateUnit));
            }
        }

        private String writePercentile(double v) {
            return  "p" + df.format(v * 100).replace(".", "");
        }

        private String writeCount(double v) {
            return  "c" + df.format(v * 100).replace(".", "");
        }
    }

    private static class TimerSerializer extends AbstractElasticMeterSerializer<ElasticTimer, Timer> {

        TimerSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticTimer.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, Timer meter) throws IOException {
            final HistogramSnapshot snapshot = meter.takeSnapshot(false);

            json.writeNumberField("count", snapshot.count());
            json.writeNumberField("max", snapshot.max(durationUnit));
            json.writeNumberField("mean", snapshot.mean(durationUnit));
            json.writeNumberField("sum", snapshot.total(durationUnit));

            writePercentiles(json, snapshot.percentileValues());
            writeCounts(json, snapshot.histogramCounts());
        }
    }

    private static class FunctionTimerSerializer extends AbstractElasticMeterSerializer<ElasticFunctionTimer, FunctionTimer> {

        FunctionTimerSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticFunctionTimer.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, FunctionTimer meter) throws IOException {
            json.writeNumberField("sum", meter.totalTime(durationUnit));
            json.writeNumberField("count", meter.count());
            json.writeNumberField("mean", meter.count());
        }
    }

    private static class DistributionSummarySerializer extends AbstractElasticMeterSerializer<ElasticDistributionSummary, DistributionSummary> {

        DistributionSummarySerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticDistributionSummary.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, DistributionSummary meter) throws IOException {
            final HistogramSnapshot snapshot = meter.takeSnapshot(false);

            json.writeNumberField("count", snapshot.count());
            json.writeNumberField("max", snapshot.max(durationUnit));
            json.writeNumberField("mean", snapshot.mean(durationUnit));
            json.writeNumberField("sum", snapshot.total(durationUnit));

            writePercentiles(json, snapshot.percentileValues());
            writeCounts(json, snapshot.histogramCounts());
        }
    }

    private static class LongTaskTimerSerializer extends AbstractElasticMeterSerializer<ElasticLongTaskTimer, LongTaskTimer> {

        LongTaskTimerSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticLongTaskTimer.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, LongTaskTimer meter) throws IOException {
            json.writeNumberField("active_tasks", meter.activeTasks());
            json.writeNumberField("duration", meter.duration(durationUnit));
        }
    }

    private static class CounterSerializer extends AbstractElasticMeterSerializer<ElasticCounter, Counter> {

        CounterSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticCounter.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, Counter meter) throws IOException {
            json.writeNumberField("count", meter.count());
        }
    }

    private static class FunctionCounterSerializer extends AbstractElasticMeterSerializer<ElasticFunctionCounter, FunctionCounter> {

        FunctionCounterSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticFunctionCounter.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, FunctionCounter meter) throws IOException {
            json.writeNumberField("count", meter.count());
        }
    }

    private static class GaugeSerializer extends AbstractElasticMeterSerializer<ElasticGauge, Gauge> {

        GaugeSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticGauge.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, Gauge meter) throws IOException {
            json.writeNumberField("value", meter.value());
        }
    }

    private static class TimeGaugeSerializer extends AbstractElasticMeterSerializer<ElasticTimeGauge, TimeGauge> {

        TimeGaugeSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticTimeGauge.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }


        @Override
        protected void serialize(JsonGenerator json, TimeGauge meter) throws IOException {
            json.writeNumberField("value", meter.value(durationUnit));
        }
    }

    private static class MeterSerializer extends AbstractElasticMeterSerializer<ElasticMeter, Meter> {

        MeterSerializer(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
            super(ElasticMeter.class, namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix);
        }

        @Override
        protected void serialize(JsonGenerator json, Meter meter) throws IOException {
            for (Measurement measurement : meter.measure()) {
                String fieldKey = measurement.getStatistic().toString()
                    .replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();

                json.writeNumberField(fieldKey, measurement.getValue());
            }
        }
    }


    private static class BulkIndexOperationHeaderSerializer extends StdSerializer<BulkIndexOperationHeader> {

        protected BulkIndexOperationHeaderSerializer() {
            super(BulkIndexOperationHeader.class);
        }

        @Override
        public void serialize(BulkIndexOperationHeader value, JsonGenerator json, SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeObjectFieldStart("index");
            if (value.index != null) {
                json.writeStringField("_index", value.index);
            }
            if (value.type != null) {
                json.writeStringField("_type", value.type);
            }
            json.writeEndObject();
            json.writeEndObject();
        }
    }

    public static class BulkIndexOperationHeader {
        public String index;
        public String type;

        public BulkIndexOperationHeader(String index, String type) {
            this.index = index;
            this.type = type;
        }
    }


    private final NamingConvention namingConvention;
    private final TimeUnit rateUnit;
    private final TimeUnit durationUnit;
    private final String timeStampFieldName;
    private final String metricPrefix;

    ElasticMetricsModule(NamingConvention namingConvention, TimeUnit rateUnit, TimeUnit durationUnit, String timeStampFieldName, String metricPrefix) {
        this.namingConvention = namingConvention;
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.timeStampFieldName = timeStampFieldName;
        this.metricPrefix = metricPrefix;
    }

    @Override
    public String getModuleName() {
        return VERSION.getArtifactId();
    }

    @Override
    public Version version() {
        return VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializers(new SimpleSerializers(Arrays.asList(
            new TimerSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new FunctionTimerSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new DistributionSummarySerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new LongTaskTimerSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new CounterSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new FunctionCounterSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new GaugeSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new TimeGaugeSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new MeterSerializer(namingConvention, rateUnit, durationUnit, timeStampFieldName, metricPrefix),
            new BulkIndexOperationHeaderSerializer()
        )));
    }
}
