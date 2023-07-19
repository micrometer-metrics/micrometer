/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.boot2.samples.components;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class PersonController {

    private final List<String> people = Arrays.asList("mike", "suzy");

    private final MeterRegistry registry;

    public PersonController(MeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/people")
    @Timed(percentiles = { 0.5, 0.95, 0.999 }, histogram = true)
    public List<String> allPeople() {
        try {
            Thread.sleep(200);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        return people;
    }

    @GetMapping("/api/person/{id}")
    @Timed("person.requests")
    public Person person(@PathVariable String id) {
        String userType = "0".equals(id) ? "admin" : "regular";
        Counter.builder("person.requests").tag("type", userType).register(registry).increment();

        return new Person(id, "jon", "schneider", "USA", "MO");
    }

    @GetMapping("/api/peopleAsync")
    public CompletableFuture<Collection<String>> personNamesAsync() {
        return CompletableFuture.supplyAsync(() -> Collections.singletonList("jon"));
    }

    /**
     * Fallback for {@link PersonController#allPeople()}
     * @return people
     */
    @SuppressWarnings("unused")
    public List<String> fallbackPeople() {
        return Arrays.asList("old mike", "fallback frank");
    }

    @GetMapping("/api/fail")
    public String fail() {
        throw new RuntimeException("boom");
    }

    @GetMapping("/api/stats")
    public Map<String, Number> stats() {
        Timer t = registry.find("http.server.requests").tags("uri", "/api/people").timer();

        Map<String, Number> result = null;

        if (t != null) {
            result = new HashMap<>();

            result.put("count", t.count());
            result.put("max", t.max(TimeUnit.MILLISECONDS));
            result.put("mean", t.mean(TimeUnit.MILLISECONDS));
        }

        return result;
    }

}
