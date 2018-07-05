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
import io.micrometer.core.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents pre-aggregated measurements
 * https://docs.appoptics.com/api/?shell#create-a-measurement
 *
 * @author Hunter Sherman
 */
public class AggregateMeasurement implements Measurement {

    private final String name;
    private final double sum;
    private final long count;
    @Nullable
    private final Double max;
    private final Map<String, String> tags;

    private AggregateMeasurement(Builder builder) {
        name = builder.name;
        sum = builder.sum;
        count = builder.count;
        max = builder.max;
        tags = builder.tags;
    }

    public String appendJson(StringBuilder json) {
        json.append("{\"name\":\"").append(name)
            .append("\",\"sum\":").append(sum)
            .append(",\"count\":").append(count);
        if(null != max) {
            json.append(",\"max\":").append(max);
        }
        if(null != tags && !tags.isEmpty()) {
            json.append(",\"tags\":").append(Measurement.tagsJson(tags));
        }
        return json.append("},").toString();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private double sum;
        private long count;
        private Double max;
        private Map<String, String> tags;

        private Builder() { }

        public Builder withName(String val) {
            name = Sanitizer.NAME_SANITIZER.apply(val);
            return this;
        }

        public Builder withSum(double val) {
            sum = val;
            return this;
        }

        public Builder withCount(long val) {
            count = val;
            return this;
        }

        public Builder withMax(Double val) {
            max = val;
            return this;
        }

        public Builder withTags(List<Tag> tags) {
            this.tags = tags.stream().collect(Collectors.toMap(
                tag -> Sanitizer.TAG_NAME_SANITIZER.apply(tag.getKey()),
                tag -> Sanitizer.TAG_VALUE_SANITIZER.apply(tag.getValue())));
            return this;
        }

        public AggregateMeasurement build() {
            return new AggregateMeasurement(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregateMeasurement that = (AggregateMeasurement) o;
        return Double.compare(that.sum, sum) == 0 &&
            count == that.count &&
            Objects.equals(name, that.name) &&
            Objects.equals(max, that.max) &&
            Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, sum, count, max, tags);
    }
}
