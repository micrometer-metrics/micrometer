/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.RequestContext;
import io.micrometer.observation.Observation;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A {@link Map}-based {@link RequestContext}. (For test purposes only).
 *
 * @author Marcin Grzejszczak
 * @author Greg Turnquist
 * @since 1.10.0
 */
class TestRequestContext implements RequestContext {

    private final Map<Object, Object> map = new HashMap<>();

    @Override
    public <T> T get(Object key) {
        return (T) map.get(key);
    }

    @Override
    public boolean hasKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void put(Object key, Object value) {
        map.put(key, value);
    }

    @Override
    public void delete(Object key) {
        map.remove(key);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Stream<Map.Entry<Object, Object>> stream() {
        return map.entrySet().stream();
    }

    static TestRequestContext withObservation(Observation value) {

        TestRequestContext testRequestContext = new TestRequestContext();
        testRequestContext.put(Observation.class, value);
        return testRequestContext;
    }

}
