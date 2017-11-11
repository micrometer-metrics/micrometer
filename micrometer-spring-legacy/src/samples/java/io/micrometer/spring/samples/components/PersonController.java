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
package io.micrometer.spring.samples.components;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class PersonController {
    private List<String> people = Arrays.asList("mike", "suzy");
    private final MeterRegistry registry;

    public PersonController(MeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/people")
    @Timed(percentiles = {0.5, 0.95, 0.999}, histogram = true)
    public List<String> allPeople() {
        Timer.builder("the.timer").tags("first tag", "tagVal0", "otherTag", "otherVal1").register(registry).record(() -> {
            ActiveSpan orderSpan = GlobalTracer.get().buildSpan("order_span").startActive();
            Timer.builder("inner.timer")
                .tags("first tag", "tagVal1").register(registry).record(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            orderSpan.deactivate();
        });

        return people;
    }

    @GetMapping("/api/stats")
    public Map<String, Number> stats() {
        return registry.find("http.server.requests").tags("uri", "/api/people")
            .timer()
            .map(t -> new HashMap<String, Number>() {{
                put("count", t.count());
                put("max", t.max(TimeUnit.MILLISECONDS));
                put("mean", t.mean(TimeUnit.MILLISECONDS));
                put("50.percentile", t.percentile(0.5, TimeUnit.MILLISECONDS));
                put("95.percentile", t.percentile(0.95, TimeUnit.MILLISECONDS));
            }})
            .orElse(null);
    }
}
