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
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchAggregateBuilderTest {
    // doesn't have to be a CloudWatch registry for aggregation to function
    private MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void addMeters() {
        registry.counter("http.requests", "method", "GET", "uri", "/api/foo", "host", "h1", "service", "myservice").increment();
        registry.counter("http.requests", "method", "GET", "uri", "/api/foo", "host", "h2", "service", "myservice").increment();
        registry.counter("http.requests", "method", "GET", "uri", "/api/bar", "host", "h3", "service", "myservice").increment();
        registry.counter("http.requests", "method", "POST", "uri", "/api/foo", "host", "h4", "service", "myservice").increment();
        registry.counter("my.counter", "host", "h1");
        registry.gauge("my.gauge", Tags.of("host", "h1"), registry, r -> r.getMeters().size());
    }

    @Test
    void aggregateId() {
        Search search = registry.find("http.requests").tags("method", "GET");
        CloudWatchAggregateBuilder builder = new CloudWatchAggregateBuilder(search, true, "service");

        assertThat(builder.aggregateId(search.meters()))
                .satisfies(id -> assertThat(id.getName()).isEqualTo("http.requests"))
                .satisfies(id -> assertThat(id.getTags().stream().map(Tag::getKey)).containsExactly("method"));
    }

    @Test
    void groupsAggregatesByName() {
        CloudWatchAggregateBuilder aggregate = new CloudWatchAggregateBuilder(Search.search(registry), true);

        assertThat(aggregate.aggregates().map(m -> m.getId().getName()))
                .containsExactly("http.requests", "my.counter", "my.gauge");
    }

    @Test
    void aggregateCounter() {
        CloudWatchAggregateBuilder aggregate = new CloudWatchAggregateBuilder(registry.find("http.requests"), true);
        assertThat(aggregate.aggregates())
                .hasOnlyOneElementSatisfying(m -> assertThat(m)
                        .isInstanceOf(Counter.class)
                        .satisfies(m2 -> assertThat(((Counter) m2).count()).isEqualTo(4)));
    }
}