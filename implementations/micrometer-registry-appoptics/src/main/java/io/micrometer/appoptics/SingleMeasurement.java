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

import io.micrometer.core.instrument.Tag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a single measurement
 * https://docs.appoptics.com/api/?shell#create-a-measurement
 *
 * @author Hunter Sherman
 */
public class SingleMeasurement implements Measurement {

    private final String name;
    private final double value;
    private final Map<String, String> tags;

    private SingleMeasurement(Builder builder) {
        this.name = builder.name;
        this.value = builder.value;
        this.tags = builder.tags;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String appendJson(StringBuilder json) {
        json
            .append("{\"name\":\"").append(name)
            .append("\",\"value\":").append(value);
        if(null != tags && !tags.isEmpty()) {
            json.append(",\"tags\":").append(Measurement.tagsJson(tags));
        }
        return json.append("},").toString();
    }

    public static final class Builder {
        private String name;
        private double value;
        private Map<String, String> tags;

        private Builder() {}

        public Builder withName(String val) {
            name = Sanitizer.NAME_SANITIZER.apply(val);
            return this;
        }

        public Builder withValue(double val) {
            value = val;
            return this;
        }

        public Builder withTags(List<Tag> tags) {
            this.tags = tags.stream().collect(Collectors.toMap(
                tag -> Sanitizer.TAG_NAME_SANITIZER.apply(tag.getKey()),
                tag -> Sanitizer.TAG_VALUE_SANITIZER.apply(tag.getValue())));
            return this;
        }

        public SingleMeasurement build() {
            return new SingleMeasurement(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SingleMeasurement that = (SingleMeasurement) o;
        return Double.compare(that.value, value) == 0 &&
            Objects.equals(name, that.name) &&
            Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, value, tags);
    }
}
