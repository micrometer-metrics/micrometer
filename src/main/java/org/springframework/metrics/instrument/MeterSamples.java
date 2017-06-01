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

import org.springframework.metrics.instrument.internal.MeterId;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.metrics.instrument.internal.MeterId.*;

public class MeterSamples {
  private final String name;
  private final Meter.Type type;
  private final List<Sample> samples = new ArrayList<>();

  public MeterSamples(String name, Meter.Type type) {
    this.name = name;
    this.type = type;
  }

  public MeterSamples(String name, Meter.Type type, Tag tag, Double value){
    this(name, type);
    samples.add(new Sample(id(name, tag), value));
  }


  public MeterSamples(String name, Meter.Type type, List<Tag> tags, Double value){
    this(name, type);
    samples.add(new Sample(id(name, tags), value));
  }

  public String getName() {
    return name;
  }

  public Meter.Type getType() {
    return type;
  }

  public List<Sample> getSamples() {
    return samples;
  }

  public static class Sample {
    private final MeterId id;
    private final Double value;

    public Sample(MeterId id, Double value) {
      this.id = id;
      this.value = value;
    }

    public MeterId getId() {
      return id;
    }

    public Double getValue() {
      return value;
    }
  }


}
