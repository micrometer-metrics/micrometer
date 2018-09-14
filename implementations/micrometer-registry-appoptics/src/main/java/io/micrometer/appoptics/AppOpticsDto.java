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
package io.micrometer.appoptics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Data Transfer Object representing the payload sent to AppOptics
 *
 * @author Hunter Sherman
 */
public class AppOpticsDto {

    private final long time;
    private final int period;
    private final Map<String, String> tags;
    private final List<Measurement> measurements;

    private AppOpticsDto(Builder builder) {
        this.time = builder.time;
        this.period = builder.period;
        this.tags = Collections.unmodifiableMap(builder.tags);
        this.measurements = Collections.unmodifiableList(builder.measurements);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public List<Measurement> getMeasurements() {
        return this.measurements;
    }

    public List<AppOpticsDto> batch(int size) {

        if(this.measurements.size() <= size) {
            return Collections.singletonList(this);
        }
        final List<AppOpticsDto> batches = new ArrayList<>();
        for (int i = 0; i < this.measurements.size(); i += size) {
            batches.add(AppOpticsDto.newBuilder()
                .withPeriod(this.period)
                .withTime(this.time)
                .withTags(this.tags)
                .withMeasurements(
                    this.measurements.subList(i, Math.min(i + size, this.measurements.size()))
                )
                .build());
        }
        return batches;
    }

    public String toJson() {

        final StringBuilder json = new StringBuilder("{\"time\":").append(time)
            .append(",\"period\":").append(period).append(",");
        if(null != tags && !tags.isEmpty()) {
            json.append("\"tags\":").append(Measurement.tagsJson(tags)).append(",");
        }
        json.append("\"measurements\":[");
        measurements.stream()
            .forEach(m -> m.appendJson(json));
        json.setLength(json.length() - 1); // Remove trailing comma
        return json.append("]}").toString();
    }

    public static final class Builder {
        private long time;
        private int period;
        private Map<String, String> tags = new HashMap<>();
        private List<Measurement> measurements = new ArrayList<>();

        public Builder withTime(long val) {
            time = val;
            return this;
        }

        public Builder withPeriod(int val) {
            period = val;
            return this;
        }

        private Builder withTags(Map<String, String> val) {
            this.tags.putAll(val);
            return this;
        }

        public Builder withTag(String key, String val) {
            this.tags.put(
                Sanitizer.TAG_NAME_SANITIZER.apply(key),
                Sanitizer.TAG_VALUE_SANITIZER.apply(val));
            return this;
        }

        public Builder withMeasurements(List<Measurement> measurements) {
            this.measurements.addAll(measurements);
            return this;
        }

        public Builder withMeasurements(Stream<Measurement> measurementStream) {
            measurementStream.forEach( this.measurements::add);
            return this;
        }

        public Builder withMeasurement(Measurement measurement) {
            this.measurements.add(measurement);
            return this;
        }

        public AppOpticsDto build() {
            return new AppOpticsDto(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppOpticsDto that = (AppOpticsDto) o;
        return time == that.time &&
            period == that.period &&
            Objects.equals(tags, that.tags) &&
            Objects.equals(measurements, that.measurements);
    }

    @Override
    public int hashCode() {

        return Objects.hash(time, period, tags, measurements);
    }
}
