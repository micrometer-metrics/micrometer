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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public interface TimeGauge extends Gauge {
    TimeUnit getBaseTimeUnit();

    default double value(TimeUnit unit) {
        return TimeUtils.convert(value(), getBaseTimeUnit(), unit);
    }

    static <T> Builder<T> builder(String name, T obj, TimeUnit fUnits, ToDoubleFunction<T> f) {
        return new Builder<>(name, obj, fUnits, f);
    }

    class Builder<T> {
        private final String name;
        private final T obj;
        private final TimeUnit fUnits;
        private final ToDoubleFunction<T> f;
        private final List<Tag> tags = new ArrayList<>();
        private String description;

        private Builder(String name, T obj, TimeUnit fUnits, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.fUnits = fUnits;
            this.f = f;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public TimeGauge register(MeterRegistry registry) {
            return registry.more().timeGauge(new Meter.Id(name, tags, null, description),
                obj, fUnits, f);
        }
    }
}
