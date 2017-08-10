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
