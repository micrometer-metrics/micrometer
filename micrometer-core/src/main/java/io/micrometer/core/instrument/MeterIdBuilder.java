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
package io.micrometer.core.instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

public class MeterIdBuilder {

  private String name;
  private List<Tag> tags;
  private MeterRegistry registry;

  public MeterIdBuilder(String name, MeterRegistry registry) {
    this.name = name;
    this.registry = registry;
    this.tags = new ArrayList<>();
  }

  public MeterIdBuilder tag(String key, String value) {
    tags.add(new ImmutableTag(key, value));
    return this;
  }

  public Counter counter(){
    return registry.counter(name, tags);
  }

  public <T> T gauge(T obj, ToDoubleFunction<T> f){
    return registry.gauge(name, tags, obj, f);
  }

  public MeterIdBuilder tags(Iterable<Tag> moreTags) {
    for(Tag t : moreTags) {
      tags.add(t);
    }
    return this;
  }

  public AtomicLong counter(AtomicLong atomicLong) {
    return registry.counter(name, tags, atomicLong);
  }
}
