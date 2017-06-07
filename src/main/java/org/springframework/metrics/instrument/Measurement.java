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
package org.springframework.metrics.instrument;

import java.util.*;

/**
 * A measurement sampled from a meter.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 */
public final class Measurement {

    private final String name;
    private final SortedSet<Tag> tags = new TreeSet<>(Comparator.comparing(Tag::getKey));
    private final double    value;

    /**
     * Create a new instance.
     *
     * @param tags For some monitoring backends, the order of tags must remain the same from sample to sample.
     */
    public Measurement(String name, List<Tag> tags, double value) {
        this.name = name;
        this.tags.addAll(tags);
        this.value = value;
    }

    /**
     * Name of the measurement, which together with tags form a unique time series.
     */
    public String getName() {
        return name;
    }

    /**
     * Tags for the measurement, which together with name form a unique time series.
     *
     * @return An ordered set of tags. For some monitoring backends, the order of tags must remain the same from
     * sample to sample.
     */
    public SortedSet<Tag> getTags() { return tags; }

    /**
     * Value for the measurement.
     */
    public double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Measurement that = (Measurement) o;
        return Double.compare(that.value, value) == 0 && name.equals(that.name) && tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = name.hashCode();
        result = 31 * result + tags.hashCode();
        temp = Double.doubleToLongBits(value);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Measurement{" +
                "name='" + name + '\'' +
                ", tags=" + tags +
                ", value=" + value +
                '}';
    }
}
